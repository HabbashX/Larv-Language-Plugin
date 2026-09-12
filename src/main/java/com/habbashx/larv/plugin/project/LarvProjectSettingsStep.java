package com.habbashx.larv.plugin.project;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.dsl.builder.Align;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.Scanner;

import static com.intellij.ui.dsl.builder.BuilderKt.panel;

public class LarvProjectSettingsStep extends ModuleWizardStep {

    private final LarvModuleBuilder builder;
    private JPanel rootPanel;

    private final JBTextField nameField = new JBTextField();
    private final JBTextField artifactField = new JBTextField();
    private final JBTextField packageField = new JBTextField();
    private final TextFieldWithBrowseButton locationField = new TextFieldWithBrowseButton();

    private final JRadioButton buildLarvRocket = new JRadioButton("LarvRocket");
    private final JRadioButton buildNone = new JRadioButton("None");
    private final ButtonGroup buildGroup = new ButtonGroup();

    private final JBCheckBox chkMain = new JBCheckBox("Generate main.larv");
    private final JBCheckBox chkReadme = new JBCheckBox("Generate README.md");

    public LarvProjectSettingsStep(LarvModuleBuilder builder) {
        this.builder = builder;
        initData();
    }

    @SuppressWarnings("removal")
    private void initData() {
        buildGroup.add(buildLarvRocket);
        buildGroup.add(buildNone);

        nameField.setText(builder.getLarvProjectName());
        artifactField.setText(builder.getArtifactName());
        packageField.setText(builder.getFolderName());

        String path = builder.getProjectLocation() != null ? builder.getProjectLocation() : System.getProperty("user.home") + "/LarvProjects";
        locationField.setText(path);

        locationField.addBrowseFolderListener("Select Project Location", null, null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor());

        if (builder.getBuildTool() == BuildTool.LARV_ROCKET) {
            buildLarvRocket.setSelected(true);
        } else {
            buildNone.setSelected(true);
        }

        chkMain.setSelected(builder.isGenerateMainLarv());
        chkReadme.setSelected(builder.isGenerateReadme());
    }

    @Override
    public JComponent getComponent() {
        if (rootPanel == null) {
            rootPanel = panel(p -> {

                p.row("Name:", r -> {
                    r.cell(nameField).align(Align.FILL);
                    return null;
                });

                p.row("Location:", r -> {
                    r.cell(locationField).align(Align.FILL);
                    return null;
                });

                p.row("Artifact:", r -> {
                    r.cell(artifactField).align(Align.FILL);
                    return null;
                });

                p.row("Package:", r -> {
                    r.cell(packageField).align(Align.FILL);
                    return null;
                });

                p.separator(null);

                p.row("Build system:", r -> {
                    JPanel radioWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    radioWrapper.setOpaque(false);

                    radioWrapper.add(buildLarvRocket);
                    buildLarvRocket.setBorder(JBUI.Borders.emptyRight(20));
                    radioWrapper.add(buildNone);

                    r.cell(radioWrapper);
                    return null;
                });

                p.separator(null);

                p.row("", r -> {
                    r.cell(chkMain);
                    return null;
                });
                p.row("", r -> {
                    r.cell(chkReadme);
                    return null;
                });

                return null;
            });

            rootPanel.setBorder(JBUI.Borders.empty(20));

            rootPanel.setMinimumSize(new Dimension(500, -1));
        }
        return rootPanel;
    }

    @Override
    public void updateDataModel() {
        builder.setLarvProjectName(nameField.getText().trim());
        builder.setArtifactName(artifactField.getText().trim());
        builder.setFolderName(packageField.getText().trim());
        builder.setProjectLocation(locationField.getText().trim());

        builder.setBuildTool(buildLarvRocket.isSelected() ? BuildTool.LARV_ROCKET : BuildTool.NONE);
        builder.setGenerateMainLarv(chkMain.isSelected());
        builder.setGenerateReadme(chkReadme.isSelected());
    }

    @Override
    public boolean validate() {
        return !nameField.getText().trim().isEmpty() && !locationField.getText().trim().isEmpty();
    }
}