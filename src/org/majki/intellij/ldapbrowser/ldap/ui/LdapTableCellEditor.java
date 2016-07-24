package org.majki.intellij.ldapbrowser.ldap.ui;

import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBTextField;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.password.PasswordUtil;
import org.majki.intellij.ldapbrowser.dialog.LdapUserPasswordDialog;
import org.majki.intellij.ldapbrowser.ldap.LdapNode;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;

/**
 * @author Attila Majoros
 */

public class LdapTableCellEditor implements TableCellEditor {

    private LdapNode ldapNode;
    private JTable table;
    private List<CellEditorListener> listeners;
    private Object newEditorValue;
    private Object currentEditorValue;
    private int rid = new SecureRandom().nextInt(100);


    public LdapTableCellEditor(LdapNode ldapNode, JTable table) {
        this.ldapNode = ldapNode;
        this.table = table;
        this.listeners = new ArrayList<>();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        LdapAttributeTableModel.Item selectedItem = getSelectedItem();
        if (selectedItem != null) {
            if (isCellUserPassword()) {
                LdapUserPasswordDialog userPasswordDialog = new LdapUserPasswordDialog(ldapNode, selectedItem.getValue().asByteArray());
                if (userPasswordDialog.showAndGet()) {
                    byte[] newPassword = PasswordUtil.createStoragePassword(userPasswordDialog.getNewPassword(), userPasswordDialog.getAlgorithm());
                    Modification modification = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, selectedItem.getAttribute().upName(), newPassword);
                    try {
                        ldapNode.getConnection().modify(ldapNode.getDn(), modification);

                        ldapNode.refresh();
                        TableModel model = table.getModel();
                        if (model instanceof LdapAttributeTableModel) {
                            ((LdapAttributeTableModel) model).refresh();
                        }
                        table.repaint();
                    } catch (LdapException e) {
                        LdapErrorHandler.handleError(e, "Could not set new password");
                    }
                }
                return null;
            } else {
                return createTextFieldForEditing((String) value);
            }
        }
        return null;
    }

    @Override
    public Object getCellEditorValue() {
        return currentEditorValue;
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
        if (isDoubleClick(anEvent) && !isCellObjectClass()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
        return true;
    }

    @Override
    public boolean stopCellEditing() {
        currentEditorValue = newEditorValue;
        fireEditingStopped(createChangeEvent(this));
        return true;
    }

    @Override
    public void cancelCellEditing() {
        fireEditingCanceled(createChangeEvent(this));
    }

    @Override
    public void addCellEditorListener(CellEditorListener l) {
        listeners.add(l);
    }

    @Override
    public void removeCellEditorListener(CellEditorListener l) {
        listeners.remove(l);
    }

    private LdapAttributeTableModel getModel() {
        TableModel model = table.getModel();
        if (model instanceof LdapAttributeTableModel) {
            return (LdapAttributeTableModel) model;
        }
        return null;
    }

    private LdapAttributeTableModel.Item getSelectedItem() {
        int selectedRow = table.getSelectedRow();
        LdapAttributeTableModel model = getModel();
        return model != null ? selectedRow != -1 ? model.getItems().get(selectedRow) : null : null;
    }

    private boolean isCellObjectClass() {
        LdapAttributeTableModel.Item selectedItem = getSelectedItem();
        return selectedItem != null && LdapNode.OBJECTCLASS_ATTRIBUTE_NAME.equalsIgnoreCase(selectedItem.getAttribute().name());
    }

    private boolean isCellUserPassword() {
        LdapAttributeTableModel.Item selectedItem = getSelectedItem();
        return selectedItem != null && LdapNode.USERPASSWORD_ATTRIBUTE_NAME.equalsIgnoreCase(selectedItem.getAttribute().name());
    }

    private boolean isDoubleClick(EventObject eventObject) {
        return (eventObject instanceof MouseEvent) && ((MouseEvent) eventObject).getClickCount() > 1;
    }

    private ChangeEvent createChangeEvent(Object source) {
        return new ChangeEvent(source);
    }

    private void fireEditingStopped(ChangeEvent changeEvent) {
        List<CellEditorListener> listenersCopy = new ArrayList<>(listeners);
        Collections.reverse(listenersCopy);
        for (CellEditorListener listener : listenersCopy) {
            listener.editingStopped(changeEvent);
        }
    }

    private void fireEditingCanceled(ChangeEvent changeEvent) {
        List<CellEditorListener> listenersCopy = new ArrayList<>(listeners);
        Collections.reverse(listenersCopy);
        for (CellEditorListener listener : listenersCopy) {
            listener.editingCanceled(changeEvent);
        }
    }

    private JBTextField createTextFieldForEditing(String value) {
        newEditorValue = value;
        currentEditorValue = value;
        final JBTextField textField = new JBTextField(value);
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    stopCellEditing();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancelCellEditing();
                }
            }
        });
        textField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent documentEvent) {
                newEditorValue = textField.getText();
            }
        });
        return textField;
    }

}
