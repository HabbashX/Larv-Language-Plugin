package com.habbashx.larv.plugin.project;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.habbashx.larv.plugin.lang.LarvIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Registers the Larv project type in IntelliJ's "New Project" wizard.
 * Appears as a top-level entry in the project-type list (left panel).
 */
public class LarvProjectType extends ProjectTemplatesFactory {

    public static final String GROUP = "Larv";

    @Override
    public @NotNull String[] getGroups() {
        return new String[]{GROUP};
    }

    @Override
    public @NotNull ProjectTemplate[] createTemplates(String group, WizardContext context) {
        return new ProjectTemplate[]{new LarvProjectTemplate()};
    }

    @Override
    public Icon getGroupIcon(String group) {
        return LarvIcons.FILE;
    }
}