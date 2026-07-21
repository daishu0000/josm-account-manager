package com.example.josm.accountmanager;

import java.net.URI;
import java.net.Authenticator.RequestorType;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openstreetmap.josm.data.oauth.IOAuthParameters;
import org.openstreetmap.josm.data.oauth.IOAuthToken;
import org.openstreetmap.josm.data.oauth.OAuthAccessTokenHolder;
import org.openstreetmap.josm.data.oauth.OAuth20Exception;
import org.openstreetmap.josm.data.oauth.OAuth20Parameters;
import org.openstreetmap.josm.data.oauth.OAuth20Token;
import org.openstreetmap.josm.data.oauth.OAuthVersion;
import org.openstreetmap.josm.io.auth.CredentialsAgentException;
import org.openstreetmap.josm.io.auth.CredentialsManager;
import org.openstreetmap.josm.spi.preferences.Config;

/** Persists metadata in preferences and delegates secret storage to JOSM's credential manager. */
final class ProfileRepository {
    static final String PROFILES_KEY = "account-manager.profiles";
    static final String ACTIVE_PROFILE_KEY = "account-manager.active-profile";

    List<AccountProfile> findAll() {
        List<AccountProfile> profiles = new ArrayList<>();
        // JOSM's MapListSetting probes the default collection with contains(null).
        // java.util.List.of() deliberately throws for that operation, while this collection is compatible.
        for (Map<String, String> values : Config.getPref().getListOfMaps(
                PROFILES_KEY, Collections.emptyList())) {
            try {
                profiles.add(new AccountProfile(
                        values.get("id"), values.get("name"),
                        PlatformPreset.fromStoredValue(values.get("platform")), values.get("apiUrl"),
                        AuthenticationMethod.fromStoredValue(values.get("authenticationMethod"))));
            } catch (IllegalArgumentException ignored) {
                // A malformed entry must not prevent users from managing the remaining profiles.
            }
        }
        return profiles;
    }

    /**
     * Imports all accounts indexed by JOSM preferences into this plugin's profile list.
     * The credentials are copied through JOSM's credential manager and never exposed
     * through ordinary preferences.
     *
     * @return the number of newly imported profiles
     */
    int importStoredJosmAccounts() throws CredentialsAgentException {
        String currentApiUrl = AccountProfile.normalizeApiUrl(Config.getPref().get(
                "osm-server.url", PlatformPreset.OSM.apiUrl()));
        String currentHost = URI.create(currentApiUrl).getHost();
        Map<String, String> apiUrlsByHost = new LinkedHashMap<>();
        apiUrlsByHost.put(currentHost, currentApiUrl);
        for (PlatformPreset preset : PlatformPreset.values()) {
            if (!preset.apiUrl().isEmpty()) {
                String presetHost = URI.create(preset.apiUrl()).getHost();
                apiUrlsByHost.putIfAbsent(presetHost, preset.apiUrl());
            }
        }

        Set<String> basicHosts = new HashSet<>();
        Set<String> oauthHosts = new HashSet<>();
        Set<String> preferenceKeys = new HashSet<>(Config.getPref().getKeySet());
        preferenceKeys.addAll(Config.getPref().getSensitive());
        AuthenticationMethod currentMethod = "basic".equalsIgnoreCase(
                Config.getPref().get("osm-server.auth-method", "oauth20"))
                        ? AuthenticationMethod.BASIC : AuthenticationMethod.OAUTH20;
        if (currentMethod == AuthenticationMethod.BASIC) {
            basicHosts.add(currentHost);
        } else {
            oauthHosts.add(currentHost);
        }
        for (String key : preferenceKeys) {
            collectHost(key, "server.username.", basicHosts, apiUrlsByHost);
            collectHost(key, "oauth.access-token.object.OAuth20.", oauthHosts, apiUrlsByHost);
            collectHost(key, "oauth.access-token.parameters.OAuth20.", oauthHosts, apiUrlsByHost);
        }

        List<AccountProfile> profiles = findAll();
        int importedCount = 0;
        for (String host : basicHosts) {
            PasswordAuthentication credentials = CredentialsManager.getInstance()
                    .lookup(RequestorType.SERVER, host);
            if (isUsable(credentials) && importAccount(profiles, apiUrlsByHost.get(host),
                    AuthenticationMethod.BASIC, credentials, null) != null) {
                importedCount++;
            }
        }
        for (String host : oauthHosts) {
            String apiUrl = apiUrlsByHost.get(host);
            IOAuthToken token = OAuthAccessTokenHolder.getInstance()
                    .getAccessToken(apiUrl, OAuthVersion.OAuth20);
            if (token != null && importAccount(profiles, apiUrl,
                    AuthenticationMethod.OAUTH20, null, token) != null) {
                importedCount++;
            }
        }
        if (importedCount > 0) saveMetadata(profiles);
        return importedCount;
    }

    private AccountProfile importAccount(List<AccountProfile> profiles, String apiUrl,
            AuthenticationMethod method, PasswordAuthentication basicCredentials, IOAuthToken oauthToken)
            throws CredentialsAgentException {
        for (AccountProfile profile : profiles) {
            if (profile.apiUrl().equalsIgnoreCase(apiUrl)
                    && profile.authenticationMethod() == method
                    && matchesCredentials(profile, basicCredentials, oauthToken)) return null;
        }
        String host = URI.create(apiUrl).getHost();
        String name = method == AuthenticationMethod.BASIC ? basicCredentials.getUserName().trim()
                : "JOSM account (" + host + ")";
        AccountProfile imported = AccountProfile.create(
                name, PlatformPreset.fromApiUrl(apiUrl), apiUrl, method);
        if (method == AuthenticationMethod.BASIC) {
            CredentialsManager.getInstance().store(RequestorType.SERVER,
                    imported.credentialKey(), basicCredentials);
        } else {
            CredentialsManager.getInstance().storeOAuthAccessToken(imported.credentialKey(), oauthToken);
        }
        profiles.add(imported);
        return imported;
    }

    private static void collectHost(String key, String prefix, Set<String> hosts,
            Map<String, String> apiUrlsByHost) {
        if (!key.startsWith(prefix)) return;
        String host = key.substring(prefix.length());
        // Ignore credentials created by this plugin itself.
        if (host.isEmpty() || host.startsWith("account-manager-") || host.endsWith(".invalid")) return;
        hosts.add(host);
        apiUrlsByHost.putIfAbsent(host, "https://" + host + "/api");
    }

    private boolean matchesCredentials(AccountProfile profile,
            PasswordAuthentication basicCredentials, IOAuthToken oauthToken) {
        try {
            if (profile.authenticationMethod() == AuthenticationMethod.BASIC) {
                PasswordAuthentication stored = basicCredentialsFor(profile);
                return stored != null
                        && stored.getUserName().equals(basicCredentials.getUserName())
                        && Arrays.equals(stored.getPassword(), basicCredentials.getPassword());
            }
            IOAuthToken stored = tokenFor(profile);
            if (stored instanceof OAuth20Token && oauthToken instanceof OAuth20Token) {
                return ((OAuth20Token) stored).getBearerToken()
                        .equals(((OAuth20Token) oauthToken).getBearerToken());
            }
            return stored != null && stored.equals(oauthToken);
        } catch (CredentialsAgentException exception) {
            return false;
        }
    }

    void save(AccountProfile profile, String username, char[] newSecret)
            throws CredentialsAgentException, OAuth20Exception {
        if (profile.authenticationMethod() == AuthenticationMethod.BASIC) {
            saveBasicCredentials(profile, username, newSecret);
            CredentialsManager.getInstance().storeOAuthAccessToken(profile.credentialKey(), null);
        } else {
            saveOAuthToken(profile, newSecret);
            clearBasicCredentials(profile.credentialKey());
        }
        List<AccountProfile> profiles = findAll();
        profiles.removeIf(existing -> existing.id().equals(profile.id()));
        profiles.add(profile);
        saveMetadata(profiles);
    }

    IOAuthToken tokenFor(AccountProfile profile) throws CredentialsAgentException {
        return CredentialsManager.getInstance().lookupOAuthAccessToken(profile.credentialKey());
    }

    PasswordAuthentication basicCredentialsFor(AccountProfile profile) throws CredentialsAgentException {
        PasswordAuthentication credentials = CredentialsManager.getInstance()
                .lookup(RequestorType.SERVER, profile.credentialKey());
        return isUsable(credentials) ? credentials : null;
    }

    boolean hasCredentials(AccountProfile profile) {
        try {
            return profile.authenticationMethod() == AuthenticationMethod.BASIC
                    ? basicCredentialsFor(profile) != null : tokenFor(profile) != null;
        } catch (CredentialsAgentException exception) {
            return false;
        }
    }

    void delete(AccountProfile profile) throws CredentialsAgentException {
        CredentialsManager.getInstance().storeOAuthAccessToken(profile.credentialKey(), null);
        clearBasicCredentials(profile.credentialKey());
        List<AccountProfile> profiles = findAll();
        profiles.removeIf(existing -> existing.id().equals(profile.id()));
        saveMetadata(profiles);
        if (profile.id().equals(activeProfileId())) {
            Config.getPref().put(ACTIVE_PROFILE_KEY, null);
        }
    }

    String activeProfileId() {
        return Config.getPref().get(ACTIVE_PROFILE_KEY, "");
    }

    void markActive(AccountProfile profile) {
        Config.getPref().put(ACTIVE_PROFILE_KEY, profile.id());
    }

    private void saveMetadata(List<AccountProfile> profiles) {
        List<Map<String, String>> values = new ArrayList<>();
        for (AccountProfile profile : profiles) {
            Map<String, String> row = new HashMap<>();
            row.put("id", profile.id());
            row.put("name", profile.name());
            row.put("platform", profile.platform().name());
            row.put("apiUrl", profile.apiUrl());
            row.put("authenticationMethod", profile.authenticationMethod().name());
            values.add(row);
        }
        Config.getPref().putListOfMaps(PROFILES_KEY, values);
    }

    private void saveOAuthToken(AccountProfile profile, char[] newToken)
            throws CredentialsAgentException, OAuth20Exception {
        if (newToken != null && newToken.length > 0) {
            CredentialsManager.getInstance().storeOAuthAccessToken(
                    profile.credentialKey(), createToken(profile.apiUrl(), new String(newToken)));
            return;
        }
        IOAuthToken existingToken = tokenFor(profile);
        if (existingToken instanceof OAuth20Token) {
            // The signing guard is tied to the API host, so editing a URL must also rebind the token.
            String bearerToken = ((OAuth20Token) existingToken).getBearerToken();
            CredentialsManager.getInstance().storeOAuthAccessToken(
                    profile.credentialKey(), createToken(profile.apiUrl(), bearerToken));
        }
    }

    private void saveBasicCredentials(AccountProfile profile, String username, char[] newPassword)
            throws CredentialsAgentException {
        String normalizedUsername = username == null ? "" : username.trim();
        PasswordAuthentication existing = basicCredentialsFor(profile);
        char[] password = newPassword != null && newPassword.length > 0
                ? newPassword : existing == null ? null : existing.getPassword();
        if (normalizedUsername.isEmpty() || password == null || password.length == 0) {
            throw new IllegalArgumentException("Username and password must not be empty");
        }
        CredentialsManager.getInstance().store(RequestorType.SERVER, profile.credentialKey(),
                new PasswordAuthentication(normalizedUsername, password));
    }

    private static void clearBasicCredentials(String key) throws CredentialsAgentException {
        CredentialsManager.getInstance().store(RequestorType.SERVER, key,
                new PasswordAuthentication("", new char[0]));
    }

    private static boolean isUsable(PasswordAuthentication credentials) {
        return credentials != null && credentials.getUserName() != null
                && !credentials.getUserName().trim().isEmpty()
                && credentials.getPassword() != null && credentials.getPassword().length > 0;
    }

    private static IOAuthToken createToken(String apiUrl, String token) throws OAuth20Exception {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token must not be empty");
        }
        URI uri = URI.create(apiUrl);
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        IOAuthParameters parameters = new OAuth20Parameters(
                "", null, origin, apiUrl, "http://127.0.0.1:8111/oauth_authorization");
        String response = "{\"access_token\":\"" + jsonEscape(token.trim()) + "\",\"token_type\":\"Bearer\"}";
        return new OAuth20Token(parameters, response);
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
