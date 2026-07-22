package com.example.josm.accountmanager;

import static com.example.josm.accountmanager.AccountManagerI18n.trc;

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
        return this == BASIC ? trc("account_manager", "Username and password") : trc("account_manager", "OAuth 2.0");
    }
}
