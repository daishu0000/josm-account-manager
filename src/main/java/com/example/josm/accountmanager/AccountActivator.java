package com.example.josm.accountmanager;

import org.openstreetmap.josm.data.UserIdentityManager;
import org.openstreetmap.josm.data.oauth.IOAuthToken;
import org.openstreetmap.josm.data.oauth.OAuthAccessTokenHolder;
import org.openstreetmap.josm.io.auth.CredentialsAgentException;
import org.openstreetmap.josm.spi.preferences.Config;

/** Applies one profile to the JOSM connection used by subsequent API operations. */
final class AccountActivator {
    private final ProfileRepository repository;

    AccountActivator(ProfileRepository repository) {
        this.repository = repository;
    }

    void activate(AccountProfile profile) throws CredentialsAgentException {
        IOAuthToken token = repository.tokenFor(profile);
        if (token == null) {
            throw new IllegalStateException("This profile has no token");
        }
        Config.getPref().put("osm-server.url", profile.apiUrl());
        Config.getPref().put("osm-server.auth-method", "oauth20");
        OAuthAccessTokenHolder.getInstance().setAccessToken(profile.apiUrl(), token);
        UserIdentityManager.getInstance().setAnonymous();
        repository.markActive(profile);
    }
}
