package com.example.josm.accountmanager;

/** Known OSM-compatible services. A custom URL is still allowed for every profile. */
enum PlatformPreset {
    OSM("OpenStreetMap (OSM)", "https://api.openstreetmap.org/api"),
    OGF("OpenGeofiction (OGF)", "https://opengeofiction.net/api"),
    OHM("OpenHistoricalMap (OHM)", "https://www.openhistoricalmap.org/api"),
    OSMDEV("OpenStreetMap Dev (OSMDEV)","https://master.apis.dev.openstreetmap.org/api"),
    CUSTOM("Custom", "");

    private final String displayName;
    private final String apiUrl;

    PlatformPreset(String displayName, String apiUrl) {
        this.displayName = displayName;
        this.apiUrl = apiUrl;
    }

    String apiUrl() {
        return apiUrl;
    }

    static PlatformPreset fromApiUrl(String apiUrl) {
        String normalized = AccountProfile.normalizeApiUrl(apiUrl);
        for (PlatformPreset preset : values()) {
            if (!preset.apiUrl.isEmpty() && preset.apiUrl.equalsIgnoreCase(normalized)) {
                return preset;
            }
        }
        // OHM serves the same Rails API from both its website and dedicated API host.
        if ("https://api.openhistoricalmap.org/api".equalsIgnoreCase(normalized)) {
            return OHM;
        }
        return CUSTOM;
    }

    static PlatformPreset fromStoredValue(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return CUSTOM;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
