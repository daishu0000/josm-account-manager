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
        restoreActiveProfile(repository);
    }

    @Override
    public PreferenceSetting getPreferenceSetting() {
        return new AccountManagerPreference(repository);
    }

    private static void restoreActiveProfile(ProfileRepository repository) {
        String activeId = repository.activeProfileId();
        if (activeId.isEmpty()) return;
        repository.findAll().stream()
                .filter(profile -> profile.id().equals(activeId))
                .findFirst()
                .ifPresent(profile -> {
                    try {
                        new AccountActivator(repository).activate(profile);
                    } catch (RuntimeException | org.openstreetmap.josm.io.auth.CredentialsAgentException exception) {
                        Logging.error(exception);
                    }
                });
    }

}
