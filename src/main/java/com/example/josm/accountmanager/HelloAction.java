package com.example.josm.accountmanager;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.gui.MainApplication;

/** The smallest visible feature: one menu item and one dialog. */
final class HelloAction extends AbstractAction {
    HelloAction() {
        super(tr("Account Manager: Hello"));
        putValue(SHORT_DESCRIPTION, tr("Verify that the Account Manager plugin is loaded"));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                tr("Account Manager plugin is running."),
                tr("Account Manager"),
                JOptionPane.INFORMATION_MESSAGE);
    }
}
