package com.example.josm.accountmanager;

import static org.openstreetmap.josm.tools.I18n.tr;

/** Authentication mechanism used by an account profile. */
enum AuthenticationMethod {
    OAUTH20,
    BASIC;

    static AuthenticationMethod fromStoredValue(String value) {
        if (value == null || value.trim().isEmpty()) return OAUTH20;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return OAUTH20;
        }
    }

    @Override
    public String toString() {
        return this == BASIC ? tr("Username and password") : tr("OAuth 2.0");
    }
}
