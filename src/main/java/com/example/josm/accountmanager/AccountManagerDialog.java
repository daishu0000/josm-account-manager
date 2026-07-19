package com.example.josm.accountmanager;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import org.openstreetmap.josm.tools.Logging;

/** Main account management window. */
final class AccountManagerDialog extends JDialog {
    private final ProfileRepository repository;
    private final AccountActivator activator;
    private final ProfileTableModel model;
    private final JTable table;
    private final JLabel status = new JLabel(" ");

    AccountManagerDialog(Window owner, ProfileRepository repository) {
        super(owner, tr("Account Manager"), ModalityType.APPLICATION_MODAL);
        this.repository = repository;
        this.activator = new AccountActivator(repository);
        this.model = new ProfileTableModel(repository);
        this.table = new JTable(model);
        buildUi();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(45);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(280);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton add = new JButton(tr("Add"));
        JButton edit = new JButton(tr("Edit"));
        JButton delete = new JButton(tr("Delete"));
        JButton activate = new JButton(tr("Activate"));
        JButton close = new JButton(tr("Close"));
        actions.add(add);
        actions.add(edit);
        actions.add(delete);
        actions.add(activate);
        actions.add(close);

        JPanel south = new JPanel(new BorderLayout());
        south.add(status, BorderLayout.NORTH);
        south.add(actions, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        add.addActionListener(event -> editProfile(null));
        edit.addActionListener(event -> {
            AccountProfile selected = selectedProfile();
            if (selected != null) editProfile(selected);
        });
        delete.addActionListener(event -> deleteSelected());
        activate.addActionListener(event -> activateSelected());
        close.addActionListener(event -> dispose());
        table.getSelectionModel().addListSelectionListener(event -> updateStatus());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) activateSelected();
            }
        });

        setSize(760, 390);
        setLocationRelativeTo(getOwner());
        updateStatus();
    }

    private void editProfile(AccountProfile original) {
        ProfileEditorDialog.Result result = new ProfileEditorDialog(original).show(this);
        if (result == null) return;
        try {
            repository.save(result.profile, result.token);
            model.reload();
            select(result.profile);
        } catch (Exception exception) {
            showError(tr("Could not save the profile"), exception);
        } finally {
            if (result.token != null) java.util.Arrays.fill(result.token, '\0');
        }
    }

    private void deleteSelected() {
        AccountProfile selected = selectedProfile();
        if (selected == null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                tr("Delete profile ''{0}'' and its stored token?", selected.name()),
                tr("Delete account profile"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            repository.delete(selected);
            model.reload();
            updateStatus();
        } catch (Exception exception) {
            showError(tr("Could not delete the profile"), exception);
        }
    }

    private void activateSelected() {
        AccountProfile selected = selectedProfile();
        if (selected == null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                tr("Activate ''{0}''? Future downloads and uploads will use {1}. Existing layers are not moved.",
                        selected.name(), selected.apiUrl()),
                tr("Switch account"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        try {
            activator.activate(selected);
            model.fireTableDataChanged();
            select(selected);
            updateStatus();
        } catch (Exception exception) {
            showError(tr("Could not activate the profile"), exception);
        }
    }

    private AccountProfile selectedProfile() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        return model.get(table.convertRowIndexToModel(viewRow));
    }

    private void select(AccountProfile profile) {
        int modelRow = model.indexOf(profile.id());
        if (modelRow >= 0) {
            int viewRow = table.convertRowIndexToView(modelRow);
            table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        }
    }

    private void updateStatus() {
        AccountProfile selected = selectedProfile();
        if (selected == null) {
            status.setText(tr("Select a profile. Double-clicking also activates it."));
        } else {
            status.setText(repository.hasToken(selected)
                    ? tr("Token is stored in the current JOSM credential manager.")
                    : tr("No token is stored. Edit this profile before activating it."));
        }
    }

    private void showError(String message, Exception exception) {
        Logging.error(exception);
        JOptionPane.showMessageDialog(this, message + ": " + exception.getMessage(),
                tr("Account Manager"), JOptionPane.ERROR_MESSAGE);
    }

    private static final class ProfileTableModel extends AbstractTableModel {
        private final ProfileRepository repository;
        private List<AccountProfile> profiles = new ArrayList<>();
        private final String[] columns = {tr("Active"), tr("Name"), tr("Platform"), tr("API URL"), tr("Token")};

        ProfileTableModel(ProfileRepository repository) {
            this.repository = repository;
            reload();
        }

        void reload() {
            profiles = repository.findAll();
            fireTableDataChanged();
        }

        AccountProfile get(int row) { return profiles.get(row); }

        int indexOf(String id) {
            for (int index = 0; index < profiles.size(); index++) {
                if (profiles.get(index).id().equals(id)) return index;
            }
            return -1;
        }

        @Override public int getRowCount() { return profiles.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override public Object getValueAt(int row, int column) {
            AccountProfile profile = profiles.get(row);
            switch (column) {
                case 0: return profile.id().equals(repository.activeProfileId()) ? "✓" : "";
                case 1: return profile.name();
                case 2: return profile.platform().toString();
                case 3: return profile.apiUrl();
                case 4: return repository.hasToken(profile) ? tr("Stored") : tr("Missing");
                default: return "";
            }
        }
    }
}
