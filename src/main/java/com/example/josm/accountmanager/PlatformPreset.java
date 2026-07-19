package com.example.josm.accountmanager;

/** Known OSM-compatible services. A custom URL is still allowed for every profile. */
enum PlatformPreset {
    OSM("OpenStreetMap (OSM)", "https://api.openstreetmap.org/api"),
    OGF("OpenGeofiction (OGF)", "https://opengeofiction.net/api"),
    OHM("OpenHistoricalMap (OHM)", "https://www.openhistoricalmap.org/api"),
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
