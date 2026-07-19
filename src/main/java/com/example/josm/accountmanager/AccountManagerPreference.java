package com.example.josm.accountmanager;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.tools.GBC;

/** Adds account management to JOSM's existing OSM Server preference page. */
final class AccountManagerPreference implements SubPreferenceSetting {
    private final ProfileRepository repository;

    AccountManagerPreference(ProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        // JOSM replaces the lazy OSM Server placeholder immediately after sub-settings
        // are initialized, so attach the section on the next Swing event-loop pass.
        SwingUtilities.invokeLater(() -> addAccountSection(gui));
    }

    private void addAccountSection(PreferenceTabbedPane gui) {
        PreferenceTabbedPane.PreferencePanel serverPanel = findServerPanel(gui, gui.getServerPreference());
        if (serverPanel == null) return;

        JPanel section = new JPanel(new BorderLayout(8, 8));
        section.setBorder(BorderFactory.createTitledBorder(tr("Account Manager")));
        JLabel summary = new JLabel();
        JButton manage = new JButton(tr("Manage accounts..."));
        updateSummary(summary);
        manage.addActionListener(event -> {
            Window owner = SwingUtilities.getWindowAncestor(gui);
            new AccountManagerDialog(owner, repository).setVisible(true);
            updateSummary(summary);
        });
        section.add(summary, BorderLayout.CENTER);
        section.add(manage, BorderLayout.LINE_END);

        Component glue = removeTrailingGlue(serverPanel);
        serverPanel.add(section, GBC.eol().fill(GBC.HORIZONTAL).insets(-5, 0, 0, 0));
        serverPanel.add(glue == null ? Box.createVerticalGlue() : glue,
                GBC.eol().fill(GBC.BOTH).weight(0, 1));
        serverPanel.revalidate();
        serverPanel.repaint();
    }

    private void updateSummary(JLabel summary) {
        String activeId = repository.activeProfileId();
        String activeName = repository.findAll().stream()
                .filter(profile -> profile.id().equals(activeId))
                .map(AccountProfile::name)
                .findFirst()
                .orElse(null);
        summary.setText(activeName == null
                ? tr("No account profile is active.")
                : tr("Active account: {0}", activeName));
    }

    private static Component removeTrailingGlue(Container panel) {
        Component[] components = panel.getComponents();
        if (components.length == 0) return null;
        Component last = components[components.length - 1];
        if (last instanceof Box.Filler) {
            panel.remove(last);
            return last;
        }
        return null;
    }

    private static PreferenceTabbedPane.PreferencePanel findServerPanel(
            Container parent, TabPreferenceSetting serverPreference) {
        for (Component component : parent.getComponents()) {
            if (component instanceof PreferenceTabbedPane.PreferencePanel) {
                PreferenceTabbedPane.PreferencePanel panel = (PreferenceTabbedPane.PreferencePanel) component;
                if (panel.getTabPreferenceSetting() == serverPreference) return panel;
            }
            if (component instanceof Container) {
                PreferenceTabbedPane.PreferencePanel found =
                        findServerPanel((Container) component, serverPreference);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    public boolean ok() {
        return false;
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    @Override
    public TabPreferenceSetting getTabPreferenceSetting(PreferenceTabbedPane gui) {
        return gui.getServerPreference();
    }
}
