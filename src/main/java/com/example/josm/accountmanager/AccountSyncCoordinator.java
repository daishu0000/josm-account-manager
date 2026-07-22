package com.example.josm.accountmanager;

import java.util.Optional;

import org.openstreetmap.josm.data.osm.UserInfo;
import org.openstreetmap.josm.io.auth.CredentialsAgentException;

/**
 * Keeps the profile selected by Account Manager aligned with the account JOSM
 * actually uses. JOSM's connection settings are the runtime source of truth.
 */
final class AccountSyncCoordinator {
    private final ProfileRepository repository;
    private final AccountActivator activator;

    AccountSyncCoordinator(ProfileRepository repository) {
        this.repository = repository;
        this.activator = new AccountActivator(repository);
    }

    /**
     * Imports credentials known to JOSM and derives the active profile from the
     * resulting runtime settings. An unmatched or incomplete JOSM account clears
     * the active marker instead of leaving a stale selection behind.
     */
    Optional<AccountProfile> reconcileFromJosm() throws CredentialsAgentException {
        repository.importStoredJosmAccounts();
        Optional<AccountProfile> current = repository.currentJosmProfile();
        if (current.isPresent()) {
            repository.markActive(current.get());
        } else {
            repository.clearActive();
        }
        return current;
    }

    /**
     * Reconciles a selection currently shown in JOSM's native preferences, even
     * before that dialog has written its URL and authentication method to prefs.
     */
    Optional<AccountProfile> reconcileFromJosm(String apiUrl, AuthenticationMethod method)
            throws CredentialsAgentException {
        if (method == AuthenticationMethod.OAUTH20) {
            repository.importStoredJosmAccounts(apiUrl);
        } else {
            repository.importStoredJosmAccounts();
        }
        Optional<AccountProfile> current = repository.currentJosmProfile(apiUrl, method);
        if (current.isPresent()) {
            repository.markActive(current.get());
        } else {
            repository.clearActive();
        }
        return current;
    }

    /** Activates a verified profile and confirms that JOSM accepted every setting. */
    void activate(AccountProfile profile, UserInfo verifiedUser) throws CredentialsAgentException {
        activator.activate(profile, verifiedUser);
        Optional<AccountProfile> current = repository.currentJosmProfile();
        if (!current.isPresent() || !current.get().id().equals(profile.id())) {
            repository.clearActive();
            throw new IllegalStateException("JOSM did not retain the selected account settings");
        }
        repository.markActive(profile);
    }

    void activate(AccountProfile profile) throws CredentialsAgentException {
        activate(profile, null);
    }
}
