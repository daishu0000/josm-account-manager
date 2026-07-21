package com.example.josm.accountmanager;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.openstreetmap.josm.data.oauth.IOAuthToken;
import org.openstreetmap.josm.data.oauth.OAuth20Token;
import org.openstreetmap.josm.io.auth.CredentialsAgentException;
import org.openstreetmap.josm.tools.HttpClient;

/** Verifies that a profile can authenticate against its OSM-compatible API. */
final class AccountValidator {
    private static final int TIMEOUT_MILLIS = 15_000;

    private final ProfileRepository repository;

    AccountValidator(ProfileRepository repository) {
        this.repository = repository;
    }

    void validate(AccountProfile profile, String username, char[] newSecret)
            throws IOException, CredentialsAgentException {
        URL validationUrl = validationUrl(profile.apiUrl());
        HttpClient client = HttpClient.create(validationUrl, "GET")
                .setConnectTimeout(TIMEOUT_MILLIS)
                .setReadTimeout(TIMEOUT_MILLIS)
                .setAccept("application/xml")
                .setHeader("Authorization", authorizationHeader(profile, username, newSecret))
                .setReasonForRequest(tr("Validate account profile"));
        try {
            HttpClient.Response response = client.connect();
            int responseCode = response.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) return;
            if (responseCode == 401 || responseCode == 403) {
                throw new IOException(tr("The server rejected these credentials (HTTP {0}).", responseCode));
            }
            if (responseCode == 404) {
                throw new IOException(tr("The API URL is not an OSM-compatible API endpoint (HTTP 404)."));
            }
            throw new IOException(tr("The account validation request failed (HTTP {0}: {1}).",
                    responseCode, response.getResponseMessage()));
        } finally {
            client.disconnect();
        }
    }

    private String authorizationHeader(AccountProfile profile, String username, char[] newSecret)
            throws CredentialsAgentException {
        if (profile.authenticationMethod() == AuthenticationMethod.BASIC) {
            PasswordAuthentication credentials = basicCredentials(profile, username, newSecret);
            return basicAuthorizationHeader(credentials.getUserName(), credentials.getPassword());
        }
        return "Bearer " + bearerToken(profile, newSecret);
    }

    static String basicAuthorizationHeader(String username, char[] password) {
        String value = username + ":" + new String(password);
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private PasswordAuthentication basicCredentials(AccountProfile profile, String username, char[] newPassword)
            throws CredentialsAgentException {
        PasswordAuthentication stored = repository.basicCredentialsFor(profile);
        String effectiveUsername = username == null || username.trim().isEmpty()
                ? stored == null ? "" : stored.getUserName() : username.trim();
        char[] effectivePassword = newPassword != null && newPassword.length > 0
                ? newPassword : stored == null ? null : stored.getPassword();
        if (effectiveUsername.isEmpty() || effectivePassword == null || effectivePassword.length == 0) {
            throw new IllegalStateException(tr("This profile has no username and password."));
        }
        return new PasswordAuthentication(effectiveUsername, effectivePassword);
    }

    private String bearerToken(AccountProfile profile, char[] newToken)
            throws CredentialsAgentException {
        if (newToken != null && newToken.length > 0) {
            return new String(newToken).trim();
        }
        IOAuthToken storedToken = repository.tokenFor(profile);
        if (storedToken instanceof OAuth20Token) {
            return ((OAuth20Token) storedToken).getBearerToken();
        }
        throw new IllegalStateException(tr("This profile has no OAuth 2.0 token."));
    }

    static URL validationUrl(String apiUrl) throws IOException {
        String normalized = apiUrl.endsWith("/")
                ? apiUrl.substring(0, apiUrl.length() - 1)
                : apiUrl;
        return URI.create(normalized + "/0.6/user/details").toURL();
    }
}
