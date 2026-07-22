package com.example.josm.accountmanager;

import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.Logging;

/** JOSM loads this class using the Plugin-Class manifest entry. */
public final class AccountManagerPlugin extends Plugin {
    private final ProfileRepository repository;

    public AccountManagerPlugin(PluginInformation info) {
        super(info);
        repository = new ProfileRepository();
        synchronizeOnStartup(repository);
    }

    @Override
    public PreferenceSetting getPreferenceSetting() {
        return new AccountManagerPreference(repository);
    }

    private static void synchronizeOnStartup(ProfileRepository repository) {
        String lastActiveId = repository.activeProfileId();
        AccountSyncCoordinator coordinator = new AccountSyncCoordinator(repository);
        try {
            // Respect a complete account selected in JOSM's native settings. Restore
            // Account Manager's last selection only when native state is incomplete.
            if (coordinator.reconcileFromJosm().isPresent()) return;
        } catch (RuntimeException | org.openstreetmap.josm.io.auth.CredentialsAgentException exception) {
            Logging.error(exception);
        }
        if (lastActiveId.isEmpty()) return;
        repository.findAll().stream()
                .filter(profile -> profile.id().equals(lastActiveId))
                .findFirst()
                .ifPresent(profile -> {
                    try {
                        coordinator.activate(profile);
                    } catch (RuntimeException | org.openstreetmap.josm.io.auth.CredentialsAgentException exception) {
                        Logging.error(exception);
                    }
                });
    }

}
