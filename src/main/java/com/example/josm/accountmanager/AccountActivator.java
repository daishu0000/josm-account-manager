package com.example.josm.accountmanager;

import java.net.Authenticator.RequestorType;
import java.net.PasswordAuthentication;
import java.net.URI;

import org.openstreetmap.josm.data.UserIdentityManager;
import org.openstreetmap.josm.data.osm.UserInfo;
import org.openstreetmap.josm.data.oauth.IOAuthToken;
import org.openstreetmap.josm.data.oauth.OAuthAccessTokenHolder;
import org.openstreetmap.josm.io.auth.CredentialsAgentException;
import org.openstreetmap.josm.io.auth.CredentialsManager;
import org.openstreetmap.josm.spi.preferences.Config;

/** Applies one profile to the JOSM connection used by subsequent API operations. */
final class AccountActivator {
    private final ProfileRepository repository;

    AccountActivator(ProfileRepository repository) {
        this.repository = repository;
    }

    void activate(AccountProfile profile) throws CredentialsAgentException {
        activate(profile, null);
    }

    void activate(AccountProfile profile, UserInfo verifiedUser) throws CredentialsAgentException {
        Config.getPref().put("osm-server.url", profile.apiUrl());
        UserIdentityManager identityManager = UserIdentityManager.getInstance();
        if (profile.authenticationMethod() == AuthenticationMethod.BASIC) {
            PasswordAuthentication credentials = repository.basicCredentialsFor(profile);
            if (credentials == null) throw new IllegalStateException("This profile has no username and password");
            Config.getPref().put("osm-server.auth-method", "basic");
            CredentialsManager.getInstance().store(RequestorType.SERVER,
                    URI.create(profile.apiUrl()).getHost(), credentials);
            if (verifiedUser == null) {
                identityManager.setPartiallyIdentified(credentials.getUserName());
            }
        } else {
            IOAuthToken token = repository.tokenFor(profile);
            if (token == null) throw new IllegalStateException("This profile has no token");
            Config.getPref().put("osm-server.auth-method", "oauth20");
            CredentialsManager.getInstance().store(RequestorType.SERVER,
                    URI.create(profile.apiUrl()).getHost(), new PasswordAuthentication("", new char[0]));
            OAuthAccessTokenHolder.getInstance().setAccessToken(profile.apiUrl(), token);
            if (verifiedUser == null) {
                identityManager.setAnonymous();
                identityManager.initFromOAuth();
            }
        }
        if (verifiedUser != null) {
            identityManager.setFullyIdentified(verifiedUser.getDisplayName(), verifiedUser);
        }
        repository.markActive(profile);
    }
}
