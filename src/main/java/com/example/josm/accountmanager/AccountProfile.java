package com.example.josm.accountmanager;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/** Non-secret profile metadata. Passwords and tokens are deliberately not fields of this class. */
final class AccountProfile {
    private final String id;
    private final String name;
    private final PlatformPreset platform;
    private final String apiUrl;
    private final AuthenticationMethod authenticationMethod;

    AccountProfile(String id, String name, PlatformPreset platform, String apiUrl) {
        this(id, name, platform, apiUrl, AuthenticationMethod.OAUTH20);
    }

    AccountProfile(String id, String name, PlatformPreset platform, String apiUrl,
            AuthenticationMethod authenticationMethod) {
        this.id = requireText(id, "Profile id");
        this.name = requireText(name, "Name");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.apiUrl = normalizeApiUrl(apiUrl);
        this.authenticationMethod = Objects.requireNonNull(authenticationMethod, "authenticationMethod");
    }

    static AccountProfile create(String name, PlatformPreset platform, String apiUrl,
            AuthenticationMethod authenticationMethod) {
        return new AccountProfile(UUID.randomUUID().toString(), name, platform, apiUrl, authenticationMethod);
    }

    AccountProfile withDetails(String newName, PlatformPreset newPlatform, String newApiUrl,
            AuthenticationMethod newAuthenticationMethod) {
        return new AccountProfile(id, newName, newPlatform, newApiUrl, newAuthenticationMethod);
    }

    String id() { return id; }
    String name() { return name; }
    PlatformPreset platform() { return platform; }
    String apiUrl() { return apiUrl; }
    AuthenticationMethod authenticationMethod() { return authenticationMethod; }

    String credentialKey() {
        return "account-manager-" + id + ".invalid";
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        return value.trim();
    }

    static String normalizeApiUrl(String value) {
        String normalized = requireText(value, "API URL");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("API URL is invalid", exception);
        }
        if (uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("API URL must be an HTTP or HTTPS URL");
        }
        return normalized;
    }
}
