package com.example.josm.accountmanager;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.PasswordAuthentication;

import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.openstreetmap.josm.data.oauth.IOAuthParameters;
import org.openstreetmap.josm.data.oauth.OAuth20Authorization;
import org.openstreetmap.josm.data.oauth.OAuth20Token;
import org.openstreetmap.josm.data.oauth.OAuthParameters;
import org.openstreetmap.josm.data.oauth.OAuthVersion;
import org.openstreetmap.josm.data.oauth.osm.OsmScopes;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.io.remotecontrol.RemoteControl;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/** Small form used for both adding and editing a profile. */
final class ProfileEditorDialog {
    private final JTextField nameField = new JTextField(28);
    private final JComboBox<PlatformPreset> platformBox = new JComboBox<>(PlatformPreset.values());
    private final JTextField apiUrlField = new JTextField(36);
    private final JComboBox<AuthenticationMethod> authenticationBox =
            new JComboBox<>(AuthenticationMethod.values());
    private final JTextField usernameField = new JTextField(36);
    private final JLabel usernameLabel = new JLabel(tr("Username") + ':');
    private final JPasswordField tokenField = new JPasswordField(36);
    private final JLabel secretLabel = new JLabel(tr("Token") + ':');
    private final JButton authorizeButton = new JButton(tr("Authorize now (fully automatic)"));
    private final JLabel automaticAuthorizationLabel = new JLabel(tr("Automatic authorization") + ':');
    private final JLabel authorizationStatus = new JLabel(" ");
    private final AccountProfile original;

    ProfileEditorDialog(AccountProfile original, ProfileRepository repository) {
        this.original = original;
        if (original == null) {
            platformBox.setSelectedItem(PlatformPreset.OSM);
            apiUrlField.setText(PlatformPreset.OSM.apiUrl());
        } else {
            nameField.setText(original.name());
            platformBox.setSelectedItem(original.platform());
            apiUrlField.setText(original.apiUrl());
            authenticationBox.setSelectedItem(original.authenticationMethod());
            if (original.authenticationMethod() == AuthenticationMethod.BASIC) {
                try {
                    PasswordAuthentication credentials = repository.basicCredentialsFor(original);
                    if (credentials != null) usernameField.setText(credentials.getUserName());
                } catch (org.openstreetmap.josm.io.auth.CredentialsAgentException exception) {
                    Logging.error(exception);
                }
            }
        }
        platformBox.addActionListener(event -> {
            PlatformPreset selected = (PlatformPreset) platformBox.getSelectedItem();
            if (selected != null && selected != PlatformPreset.CUSTOM) {
                apiUrlField.setText(selected.apiUrl());
            }
        });
        authenticationBox.addActionListener(event -> updateAuthenticationFields());
    }

    Result show(Component parent) {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        addRow(form, constraints, 0, tr("Name"), nameField);
        addRow(form, constraints, 1, tr("Platform"), platformBox);
        addRow(form, constraints, 2, tr("API URL"), apiUrlField);
        addRow(form, constraints, 3, tr("Authentication"), authenticationBox);
        addRow(form, constraints, 4, usernameLabel, usernameField);
        addRow(form, constraints, 5, secretLabel, tokenField);
        addRow(form, constraints, 6, automaticAuthorizationLabel, authorizeButton);

        updateAuthenticationFields();

        authorizeButton.addActionListener(event -> authorizeInBrowser(parent));

        String tokenHelp = original == null
                ? tr("Credentials are stored through JOSM's credential manager.")
                : tr("Leave the secret field empty to keep the existing password or token.");
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(form, BorderLayout.CENTER);
        JPanel help = new JPanel(new BorderLayout());
        help.add(new JLabel(tokenHelp), BorderLayout.NORTH);
        help.add(authorizationStatus, BorderLayout.SOUTH);
        content.add(help, BorderLayout.SOUTH);

        while (JOptionPane.showConfirmDialog(parent, content,
                original == null ? tr("Add account profile") : tr("Edit account profile"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                PlatformPreset platform = (PlatformPreset) platformBox.getSelectedItem();
                AuthenticationMethod authenticationMethod =
                        (AuthenticationMethod) authenticationBox.getSelectedItem();
                AccountProfile profile = original == null
                        ? AccountProfile.create(nameField.getText(), platform, apiUrlField.getText(),
                                authenticationMethod)
                        : original.withDetails(nameField.getText(), platform, apiUrlField.getText(),
                                authenticationMethod);
                char[] secret = tokenField.getPassword();
                boolean canKeepExisting = original != null
                        && original.authenticationMethod() == authenticationMethod;
                if (!canKeepExisting && secret.length == 0) {
                    throw new IllegalArgumentException(authenticationMethod == AuthenticationMethod.BASIC
                            ? tr("Password must not be empty") : tr("Token must not be empty"));
                }
                if (authenticationMethod == AuthenticationMethod.BASIC
                        && usernameField.getText().trim().isEmpty()) {
                    throw new IllegalArgumentException(tr("Username must not be empty"));
                }
                return new Result(profile, usernameField.getText().trim(),
                        secret.length == 0 ? null : secret);
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(parent, exception.getMessage(), tr("Invalid profile"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        return null;
    }

    private void authorizeInBrowser(Component parent) {
        final AccountProfile profile;
        try {
            PlatformPreset platform = (PlatformPreset) platformBox.getSelectedItem();
            AuthenticationMethod authenticationMethod =
                    (AuthenticationMethod) authenticationBox.getSelectedItem();
            profile = original == null
                    ? AccountProfile.create(nameField.getText(), platform, apiUrlField.getText(),
                            authenticationMethod)
                    : original.withDetails(nameField.getText(), platform, apiUrlField.getText(),
                            authenticationMethod);
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(parent, exception.getMessage(), tr("Invalid profile"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        authorizeButton.setEnabled(false);
        authorizationStatus.setText(tr("Preparing browser authorization..."));
        MainApplication.worker.execute(() -> {
            boolean remoteControlWasEnabled = Boolean.TRUE.equals(RemoteControl.PROP_REMOTECONTROL_ENABLED.get());
            try {
                IOAuthParameters parameters = OAuthParameters.createDefault(
                        profile.apiUrl(), OAuthVersion.OAuth20);
                if (Utils.isEmpty(parameters.getClientId())) {
                    throw new IllegalStateException(tr(
                            "This server has no OAuth application registered for JOSM. Enter a token manually."));
                }
                if (!remoteControlWasEnabled) RemoteControl.start();
                new OAuth20Authorization().authorize(parameters, token -> {
                    if (!remoteControlWasEnabled) RemoteControl.stop();
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        authorizeButton.setEnabled(true);
                        if (token.isPresent() && token.get() instanceof OAuth20Token) {
                            tokenField.setText(((OAuth20Token) token.get()).getBearerToken());
                            authorizationStatus.setText(tr("Authorization succeeded. The token was obtained automatically."));
                        } else {
                            authorizationStatus.setText(tr("Authorization failed or was canceled."));
                        }
                    });
                }, OsmScopes.read_prefs, OsmScopes.write_api);
                javax.swing.SwingUtilities.invokeLater(() ->
                        authorizationStatus.setText(tr("Complete authorization in the opened browser.")));
            } catch (RuntimeException exception) {
                if (!remoteControlWasEnabled) RemoteControl.stop();
                Logging.error(exception);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    authorizeButton.setEnabled(true);
                    authorizationStatus.setText(tr("Could not start automatic authorization."));
                    JOptionPane.showMessageDialog(parent,
                            tr("Could not start automatic authorization: {0}", exception.getMessage()),
                            tr("OAuth authorization"), JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void updateAuthenticationFields() {
        boolean basic = authenticationBox.getSelectedItem() == AuthenticationMethod.BASIC;
        usernameLabel.setVisible(basic);
        usernameField.setVisible(basic);
        secretLabel.setText((basic ? tr("Password") : tr("Token")) + ':');
        automaticAuthorizationLabel.setVisible(!basic);
        authorizeButton.setVisible(!basic);
        tokenField.setToolTipText(basic ? tr("Account password") : tr("OAuth 2.0 access token"));
    }

    private static void addRow(JPanel panel, GridBagConstraints constraints, int row,
            String label, Component component) {
        addRow(panel, constraints, row, new JLabel(label + ':'), component);
    }

    private static void addRow(JPanel panel, GridBagConstraints constraints, int row,
            JLabel label, Component component) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(component, constraints);
    }

    static final class Result {
        final AccountProfile profile;
        final String username;
        final char[] secret;

        Result(AccountProfile profile, String username, char[] secret) {
            this.profile = profile;
            this.username = username;
            this.secret = secret;
        }
    }
}
