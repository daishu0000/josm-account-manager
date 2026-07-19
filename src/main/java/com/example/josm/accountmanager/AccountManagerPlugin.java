package com.example.josm.accountmanager;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

import javax.swing.JMenuItem;

/** JOSM loads this class using the Plugin-Class manifest entry. */
public final class AccountManagerPlugin extends Plugin {
    private final JMenuItem helloMenuItem;

    public AccountManagerPlugin(PluginInformation info) {
        super(info);
        helloMenuItem = MainApplication.getMenu().toolsMenu.add(new HelloAction());
    }

    /** Allows JOSM to unload/reload the plugin without leaving its menu item behind. */
    public void preReloadCleanup() {
        MainApplication.getMenu().toolsMenu.remove(helloMenuItem);
    }
}
