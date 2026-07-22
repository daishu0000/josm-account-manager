package com.example.josm.accountmanager;

import static com.example.josm.accountmanager.AccountManagerI18n.trc;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openstreetmap.josm.data.oauth.IOAuthToken;
import org.openstreetmap.josm.data.oauth.OAuthAccessTokenHolder;
import org.openstreetmap.josm.data.oauth.OAuthVersion;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.server.AuthenticationPreferencesPanel;
import org.openstreetmap.josm.gui.preferences.server.OsmApiUrlInputPanel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;

/** Adds account management to JOSM's existing OSM Server preference page. */
final class AccountManagerPreference implements SubPreferenceSetting {
    private final ProfileRepository repository;
    private final AccountSyncCoordinator coordinator;

    AccountManagerPreference(ProfileRepository repository) {
        this.repository = repository;
        this.coordinator = new AccountSyncCoordinator(repository);
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
        section.setBorder(BorderFactory.createTitledBorder(trc("account_manager", "Account Manager")));
        JLabel summary = new JLabel();
        JButton manage = new JButton(trc("account_manager", "Manage accounts..."));
        updateSummary(summary);
        manage.addActionListener(event -> {
            // Native OAuth authorization keeps the freshly obtained token in JOSM's
            // OAuthAccessTokenHolder until the preferences dialog is applied. Import
            // it before opening Account Manager so it is immediately available there.
            synchronizeFromJosm(serverPanel);
            updateSummary(summary);
            Window owner = SwingUtilities.getWindowAncestor(gui);
            new AccountManagerDialog(owner, repository,
                    () -> refreshNativeAccountPanel(serverPanel)).setVisible(true);
            updateSummary(summary);
        });
        section.add(summary, BorderLayout.CENTER);
        section.add(manage, BorderLayout.LINE_END);
        observeNativeOAuthChanges(serverPanel, summary);

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

    private void observeNativeOAuthChanges(Container serverPanel, JLabel summary) {
        final String[] observedApiUrl = {null};
        final String[] observedToken = {null};
        final boolean[] observationInitialized = {false};
        final AtomicBoolean synchronizationInProgress = new AtomicBoolean();
        Timer timer = new Timer(750, event -> {
            OsmApiUrlInputPanel apiUrlPanel = findApiUrlPanel(serverPanel);
            if (apiUrlPanel == null) return;

            // The native URL field can temporarily be empty or incomplete while the
            // user edits it. That is ordinary input state, not an error worth sending
            // to JOSM's bug-report queue.
            String apiUrl;
            try {
                apiUrl = AccountProfile.normalizeApiUrl(effectiveApiUrl(apiUrlPanel));
            } catch (IllegalArgumentException exception) {
                return;
            }
            if (!synchronizationInProgress.compareAndSet(false, true)) return;

            // Swing state must be read on the EDT. Credential backends, preference
            // scans and profile matching can be slow, so everything else runs in a
            // worker instead of freezing the Preferences dialog.
            CompletableFuture.runAsync(() -> {
                try {
                    IOAuthToken token = OAuthAccessTokenHolder.getInstance()
                            .getAccessToken(apiUrl, OAuthVersion.OAuth20);
                    String tokenValue = oauthTokenValue(token);
                    // The current account was already reconciled at plugin startup.
                    // Treat the first poll as a baseline rather than a change; doing
                    // a full credential scan here competes with JOSM's native panel
                    // while it is performing its own first-time initialization.
                    if (!observationInitialized[0]) {
                        observedApiUrl[0] = apiUrl;
                        observedToken[0] = tokenValue;
                        observationInitialized[0] = true;
                        return;
                    }
                    if (apiUrl.equals(observedApiUrl[0])
                            && Objects.equals(tokenValue, observedToken[0])) return;

                    // Remember null as well, otherwise an account using Basic auth
                    // would cause the same lookup on every timer tick. A transition
                    // from null to a real OAuth token is still detected.
                    observedApiUrl[0] = apiUrl;
                    observedToken[0] = tokenValue;
                    if (token == null) return;

                    coordinator.reconcileFromJosm(apiUrl, AuthenticationMethod.OAUTH20);
                    SwingUtilities.invokeLater(() -> updateSummary(summary));
                } catch (RuntimeException | org.openstreetmap.josm.io.auth.CredentialsAgentException exception) {
                    Logging.error(exception);
                } finally {
                    synchronizationInProgress.set(false);
                }
            });
        });
        timer.setRepeats(true);
        serverPanel.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
            if (serverPanel.isShowing()) {
                timer.start();
            } else {
                timer.stop();
            }
        });
        if (serverPanel.isShowing()) timer.start();
    }

    private static String oauthTokenValue(IOAuthToken token) {
        if (token instanceof org.openstreetmap.josm.data.oauth.OAuth20Token) {
            return ((org.openstreetmap.josm.data.oauth.OAuth20Token) token).getBearerToken();
        }
        return token == null ? null : token.toString();
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

    private static OsmApiUrlInputPanel findApiUrlPanel(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof OsmApiUrlInputPanel) {
                return (OsmApiUrlInputPanel) component;
            }
            if (component instanceof Container) {
                OsmApiUrlInputPanel found = findApiUrlPanel((Container) component);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String effectiveApiUrl(OsmApiUrlInputPanel panel) {
        return usesDefaultServer(panel)
                ? Config.getUrls().getDefaultOsmApiUrl()
                : panel.getStrippedApiUrl();
    }

    private static boolean usesDefaultServer(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JCheckBox && ((JCheckBox) component).isSelected()) {
                return true;
            }
            if (component instanceof Container && usesDefaultServer((Container) component)) {
                return true;
            }
        }
        return false;
    }

    private void updateSummary(JLabel summary) {
        String activeId = repository.activeProfileId();
        String activeName = repository.findAll().stream()
                .filter(profile -> profile.id().equals(activeId))
                .map(AccountProfile::name)
                .findFirst()
                .orElse(null);
        summary.setText(activeName == null
                ? trc("account_manager", "No account profile is active.")
                : trc("account_manager", "Active account: {0}", activeName));
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
            coordinator.reconcileFromJosm();
        } catch (RuntimeException | org.openstreetmap.josm.io.auth.CredentialsAgentException exception) {
            // Credential backends are pluggable; a temporary backend failure must not
            // prevent the native preferences dialog from closing.
            Logging.error(exception);
        }
    }

    private void synchronizeFromJosm(Container serverPanel) {
        try {
            // The URL field is saved only when Preferences is applied, while native
            // OAuth authorization stores its token immediately under the field's
            // pending URL. Import that token before reconciling applied state.
            OsmApiUrlInputPanel apiUrlPanel = findApiUrlPanel(serverPanel);
            if (apiUrlPanel != null) {
                coordinator.reconcileFromJosm(effectiveApiUrl(apiUrlPanel),
                        AuthenticationMethod.OAUTH20);
            } else {
                coordinator.reconcileFromJosm();
            }
        } catch (RuntimeException | org.openstreetmap.josm.io.auth.CredentialsAgentException exception) {
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
