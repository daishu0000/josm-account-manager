package com.example.josm.accountmanager;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import org.openstreetmap.josm.gui.MainApplication;

/** Opens the account manager from JOSM's Tools menu. */
final class ManageAccountsAction extends AbstractAction {
    private final ProfileRepository repository;

    ManageAccountsAction(ProfileRepository repository) {
        super(tr("Account Manager..."));
        this.repository = repository;
        putValue(SHORT_DESCRIPTION, tr("Manage and switch OSM-compatible account profiles"));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        new AccountManagerDialog(MainApplication.getMainFrame(), repository).setVisible(true);
    }
}
