package com.example.josm.accountmanager;

/** Dependency-free unit tests so the project remains fully buildable offline. */
public final class AccountProfileTest {
    private AccountProfileTest() {
    }

    public static void main(String[] args) {
        trimsMetadataAndTrailingSlashes();
        rejectsMissingNameAndInvalidUrl();
        unknownStoredPlatformFallsBackToCustom();
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
        requireThrows(() -> AccountProfile.create(" ", PlatformPreset.OSM, PlatformPreset.OSM.apiUrl()));
        requireThrows(() -> AccountProfile.create("test", PlatformPreset.CUSTOM, "not-a-url"));
    }

    private static void unknownStoredPlatformFallsBackToCustom() {
        requireEquals(PlatformPreset.CUSTOM, PlatformPreset.fromStoredValue("FUTURE_PLATFORM"));
        requireEquals(PlatformPreset.CUSTOM, PlatformPreset.fromStoredValue(null));
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
