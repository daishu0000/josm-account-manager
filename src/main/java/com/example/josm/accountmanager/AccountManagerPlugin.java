package com.example.josm.accountmanager;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.Logging;

import javax.swing.JMenuItem;

/** JOSM loads this class using the Plugin-Class manifest entry. */
public final class AccountManagerPlugin extends Plugin {
    private final JMenuItem menuItem;

    public AccountManagerPlugin(PluginInformation info) {
        super(info);
        ProfileRepository repository = new ProfileRepository();
        restoreActiveProfile(repository);
        menuItem = MainApplication.getMenu().toolsMenu.add(new ManageAccountsAction(repository));
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

    /** Allows JOSM to unload/reload the plugin without leaving its menu item behind. */
    public void preReloadCleanup() {
        MainApplication.getMenu().toolsMenu.remove(menuItem);
    }
}
