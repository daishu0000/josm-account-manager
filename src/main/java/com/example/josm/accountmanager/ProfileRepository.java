package com.example.josm.accountmanager;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.data.oauth.IOAuthParameters;
import org.openstreetmap.josm.data.oauth.IOAuthToken;
import org.openstreetmap.josm.data.oauth.OAuth20Exception;
import org.openstreetmap.josm.data.oauth.OAuth20Parameters;
import org.openstreetmap.josm.data.oauth.OAuth20Token;
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
                        PlatformPreset.fromStoredValue(values.get("platform")), values.get("apiUrl")));
            } catch (IllegalArgumentException ignored) {
                // A malformed entry must not prevent users from managing the remaining profiles.
            }
        }
        return profiles;
    }

    void save(AccountProfile profile, char[] newToken) throws CredentialsAgentException, OAuth20Exception {
        if (newToken != null && newToken.length > 0) {
            CredentialsManager.getInstance().storeOAuthAccessToken(
                    profile.credentialKey(), createToken(profile.apiUrl(), new String(newToken)));
        } else {
            IOAuthToken existingToken = tokenFor(profile);
            if (existingToken instanceof OAuth20Token) {
                // The signing guard is tied to the API host, so editing a URL must also rebind the token.
                String bearerToken = ((OAuth20Token) existingToken).getBearerToken();
                CredentialsManager.getInstance().storeOAuthAccessToken(
                        profile.credentialKey(), createToken(profile.apiUrl(), bearerToken));
            }
        }
        List<AccountProfile> profiles = findAll();
        profiles.removeIf(existing -> existing.id().equals(profile.id()));
        profiles.add(profile);
        saveMetadata(profiles);
    }

    IOAuthToken tokenFor(AccountProfile profile) throws CredentialsAgentException {
        return CredentialsManager.getInstance().lookupOAuthAccessToken(profile.credentialKey());
    }

    boolean hasToken(AccountProfile profile) {
        try {
            return tokenFor(profile) != null;
        } catch (CredentialsAgentException exception) {
            return false;
        }
    }

    void delete(AccountProfile profile) throws CredentialsAgentException {
        CredentialsManager.getInstance().storeOAuthAccessToken(profile.credentialKey(), null);
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
            values.add(row);
        }
        Config.getPref().putListOfMaps(PROFILES_KEY, values);
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
