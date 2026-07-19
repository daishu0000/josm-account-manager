package com.example.josm.accountmanager;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/** Non-secret profile metadata. The token is deliberately not a field of this class. */
final class AccountProfile {
    private final String id;
    private final String name;
    private final PlatformPreset platform;
    private final String apiUrl;

    AccountProfile(String id, String name, PlatformPreset platform, String apiUrl) {
        this.id = requireText(id, "Profile id");
        this.name = requireText(name, "Name");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.apiUrl = normalizeApiUrl(apiUrl);
    }

    static AccountProfile create(String name, PlatformPreset platform, String apiUrl) {
        return new AccountProfile(UUID.randomUUID().toString(), name, platform, apiUrl);
    }

    AccountProfile withDetails(String newName, PlatformPreset newPlatform, String newApiUrl) {
        return new AccountProfile(id, newName, newPlatform, newApiUrl);
    }

    String id() { return id; }
    String name() { return name; }
    PlatformPreset platform() { return platform; }
    String apiUrl() { return apiUrl; }

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
