package com.habbashx.larv.plugin.project;

import com.habbashx.larv.plugin.lang.LarvIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LarvNewClassDialog extends DialogWrapper {

    private JTextField nameField;

    private JBList<TypeItem> typeList;

    private enum Type {
        CLASS,
        ENUM,
        MODULE
    }

    private static class TypeItem {
        final String name;
        final Icon icon;
        final Type type;

        TypeItem(String name, Icon icon, Type type) {
            this.name = name;
            this.icon = icon;
            this.type = type;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public LarvNewClassDialog(@Nullable Project project) {
        super(project, true);
        setTitle("New Larv Type");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(10, 12));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = JBUI.insets(4);

        // ── Name ─────────────────────────────
        panel.add(new JBLabel("Name:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        nameField = new JTextField(24);
        nameField.setToolTipText("e.g. User, Logger, NetworkModule");
        panel.add(nameField, gbc);

        // ── spacing ──────────────────────────
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(Box.createVerticalStrut(10), gbc);

        // ── Type label ───────────────────────
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.weightx = 0;
        panel.add(new JBLabel("Type:"), gbc);

        // ── List (Java-style) ────────────────
        DefaultListModel<TypeItem> model = new DefaultListModel<>();

        model.addElement(new TypeItem("Class", LarvIcons.LARV_CLASS, Type.CLASS));
        model.addElement(new TypeItem("Enum", LarvIcons.LARV_ENUM, Type.ENUM));
        model.addElement(new TypeItem("Module", LarvIcons.LARV_MODULE, Type.MODULE));

        typeList = new JBList<>(model);
        typeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        typeList.setSelectedIndex(0);

        typeList.setCellRenderer(new ListCellRenderer<>() {
            @Override
            public Component getListCellRendererComponent(
                    JList<? extends TypeItem> list,
                    TypeItem value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus
            ) {
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(true);

                JLabel label = new JLabel(value.name, value.icon, JLabel.LEFT);
                label.setBorder(JBUI.Borders.empty(4, 6));

                if (isSelected) {
                    row.setBackground(list.getSelectionBackground());
                    label.setForeground(list.getSelectionForeground());
                } else {
                    row.setBackground(list.getBackground());
                }

                row.add(label, BorderLayout.WEST);
                return row;
            }
        });

        JScrollPane scroll = new JScrollPane(typeList);
        scroll.setPreferredSize(new Dimension(200, 120));

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        panel.add(scroll, gbc);

        return panel;
    }

    @Override
    protected void doOKAction() {
        String name = nameField.getText().trim();

        if (name.isEmpty()) {
            Messages.showWarningDialog(getContentPane(),
                    "Please enter a name.",
                    "Larv");
            return;
        }

        if (!name.matches("[A-Z][a-zA-Z0-9_]*")) {
            Messages.showWarningDialog(getContentPane(),
                    "Name must start with uppercase letter\nand contain only letters or digits.",
                    "Larv");
            return;
        }

        super.doOKAction();
    }

    // ───────── API ─────────

    public String getNameValue() {
        return nameField.getText().trim();
    }

    public String getType() {
        TypeItem item = typeList.getSelectedValue();
        return item.type.name().toLowerCase();
    }
}