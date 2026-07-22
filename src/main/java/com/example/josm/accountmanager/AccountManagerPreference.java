package com.example.josm.accountmanager;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.server.AuthenticationPreferencesPanel;
import org.openstreetmap.josm.gui.preferences.server.OsmApiUrlInputPanel;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;

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
            // Native OAuth authorization keeps the freshly obtained token in JOSM's
            // OAuthAccessTokenHolder until the preferences dialog is applied. Import
            // it before opening Account Manager so it is immediately available there.
            synchronizeFromJosm();
            updateSummary(summary);
            Window owner = SwingUtilities.getWindowAncestor(gui);
            new AccountManagerDialog(owner, repository,
                    () -> refreshNativeAccountPanel(serverPanel)).setVisible(true);
            updateSummary(summary);
        });
        section.add(summary, BorderLayout.CENTER);
        section.add(manage, BorderLayout.LINE_END);

        AuthenticationPreferencesPanel authenticationPanel =
                findAuthenticationPanel(serverPanel);
        if (authenticationPanel == null || authenticationPanel.getParent() == null) return;

        Container nativeSettings = authenticationPanel.getParent();
        int authenticationIndex = nativeSettings.getComponentZOrder(authenticationPanel);
        nativeSettings.add(section,
                GBC.eop().fill(GBC.HORIZONTAL).insets(0, 8, 0, 8),
                authenticationIndex + 1);
        serverPanel.revalidate();
        serverPanel.repaint();
    }

    private static void refreshNativeAccountPanel(Container serverPanel) {
        refreshApiUrlPanels(serverPanel);
        refreshAuthenticationPanels(serverPanel);
        serverPanel.revalidate();
        serverPanel.repaint();
    }

    private static void refreshApiUrlPanels(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof OsmApiUrlInputPanel) {
                ((OsmApiUrlInputPanel) component).initFromPreferences();
            }
            if (component instanceof Container) {
                refreshApiUrlPanels((Container) component);
            }
        }
    }

    private static void refreshAuthenticationPanels(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof AuthenticationPreferencesPanel) {
                ((AuthenticationPreferencesPanel) component).initFromPreferences();
            }
            if (component instanceof Container) {
                refreshAuthenticationPanels((Container) component);
            }
        }
    }

    private static AuthenticationPreferencesPanel findAuthenticationPanel(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof AuthenticationPreferencesPanel) {
                return (AuthenticationPreferencesPanel) component;
            }
            if (component instanceof Container) {
                AuthenticationPreferencesPanel found =
                        findAuthenticationPanel((Container) component);
                if (found != null) return found;
            }
        }
        return null;
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
        // Run after every preference setting has applied its values. This also covers
        // users who authorize through JOSM's native panel and close Preferences
        // without opening Account Manager first.
        SwingUtilities.invokeLater(this::synchronizeFromJosm);
        return false;
    }

    private void synchronizeFromJosm() {
        try {
            repository.importStoredJosmAccounts();
        } catch (RuntimeException | org.openstreetmap.josm.io.auth.CredentialsAgentException exception) {
            // Credential backends are pluggable; a temporary backend failure must not
            // prevent the native preferences dialog from closing.
            Logging.error(exception);
        }
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
