package com.example.josm.accountmanager;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

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
    private final JPasswordField tokenField = new JPasswordField(36);
    private final JButton authorizeButton = new JButton(tr("Authorize now (fully automatic)"));
    private final JLabel authorizationStatus = new JLabel(" ");
    private final AccountProfile original;

    ProfileEditorDialog(AccountProfile original) {
        this.original = original;
        if (original == null) {
            platformBox.setSelectedItem(PlatformPreset.OSM);
            apiUrlField.setText(PlatformPreset.OSM.apiUrl());
        } else {
            nameField.setText(original.name());
            platformBox.setSelectedItem(original.platform());
            apiUrlField.setText(original.apiUrl());
        }
        platformBox.addActionListener(event -> {
            PlatformPreset selected = (PlatformPreset) platformBox.getSelectedItem();
            if (selected != null && selected != PlatformPreset.CUSTOM) {
                apiUrlField.setText(selected.apiUrl());
            }
        });
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
        addRow(form, constraints, 3, tr("Token"), tokenField);
        addRow(form, constraints, 4, tr("Automatic authorization"), authorizeButton);

        authorizeButton.addActionListener(event -> authorizeInBrowser(parent));

        String tokenHelp = original == null
                ? tr("Required. The token is stored through JOSM's credential manager.")
                : tr("Leave empty to keep the existing token.");
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
                AccountProfile profile = original == null
                        ? AccountProfile.create(nameField.getText(), platform, apiUrlField.getText())
                        : original.withDetails(nameField.getText(), platform, apiUrlField.getText());
                char[] token = tokenField.getPassword();
                if (original == null && token.length == 0) {
                    throw new IllegalArgumentException(tr("Token must not be empty"));
                }
                return new Result(profile, token.length == 0 ? null : token);
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
            profile = original == null
                    ? AccountProfile.create(nameField.getText(), platform, apiUrlField.getText())
                    : original.withDetails(nameField.getText(), platform, apiUrlField.getText());
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

    private static void addRow(JPanel panel, GridBagConstraints constraints, int row,
            String label, Component component) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(new JLabel(label + ':'), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(component, constraints);
    }

    static final class Result {
        final AccountProfile profile;
        final char[] token;

        Result(AccountProfile profile, char[] token) {
            this.profile = profile;
            this.token = token;
        }
    }
}
