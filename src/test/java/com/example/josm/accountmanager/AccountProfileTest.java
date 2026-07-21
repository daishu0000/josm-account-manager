package com.example.josm.accountmanager;

/** Dependency-free unit tests so the project remains fully buildable offline. */
public final class AccountProfileTest {
    private AccountProfileTest() {
    }

    public static void main(String[] args) throws Exception {
        trimsMetadataAndTrailingSlashes();
        rejectsMissingNameAndInvalidUrl();
        unknownStoredPlatformFallsBackToCustom();
        buildsAccountValidationUrl();
        defaultsOldProfilesToOAuth();
        buildsBasicAuthorizationHeader();
    }

    private static void trimsMetadataAndTrailingSlashes() {
        AccountProfile profile = new AccountProfile(
                " profile-id ", " Main OSM ", PlatformPreset.OSM,
                "https://api.openstreetmap.org/api///");

        requireEquals("profile-id", profile.id());
        requireEquals("Main OSM", profile.name());
        requireEquals("https://api.openstreetmap.org/api", profile.apiUrl());
    }

    private static void rejectsMissingNameAndInvalidUrl() {
        requireThrows(() -> AccountProfile.create(" ", PlatformPreset.OSM, PlatformPreset.OSM.apiUrl(),
                AuthenticationMethod.OAUTH20));
        requireThrows(() -> AccountProfile.create("test", PlatformPreset.CUSTOM, "not-a-url",
                AuthenticationMethod.BASIC));
    }

    private static void defaultsOldProfilesToOAuth() {
        requireEquals(AuthenticationMethod.OAUTH20, AuthenticationMethod.fromStoredValue(null));
        requireEquals(AuthenticationMethod.OAUTH20, AuthenticationMethod.fromStoredValue("UNKNOWN"));
        requireEquals(AuthenticationMethod.BASIC, AuthenticationMethod.fromStoredValue("BASIC"));
    }

    private static void buildsBasicAuthorizationHeader() {
        requireEquals("Basic dXNlcjpwYXNz",
                AccountValidator.basicAuthorizationHeader("user", "pass".toCharArray()));
    }

    private static void unknownStoredPlatformFallsBackToCustom() {
        requireEquals(PlatformPreset.CUSTOM, PlatformPreset.fromStoredValue("FUTURE_PLATFORM"));
        requireEquals(PlatformPreset.CUSTOM, PlatformPreset.fromStoredValue(null));
    }

    private static void buildsAccountValidationUrl() throws Exception {
        requireEquals("https://api.openstreetmap.org/api/0.6/user/details",
                AccountValidator.validationUrl("https://api.openstreetmap.org/api/").toString());
        requireEquals("https://example.test/custom/api/0.6/user/details",
                AccountValidator.validationUrl("https://example.test/custom/api").toString());
    }

    private static void requireEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void requireThrows(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }
}
