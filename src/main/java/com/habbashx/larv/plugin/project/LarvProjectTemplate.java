package com.habbashx.larv.plugin.project;

import com.intellij.ide.util.projectWizard.AbstractModuleBuilder;
import com.intellij.platform.ProjectTemplate;
import com.intellij.openapi.ui.ValidationInfo;
import com.habbashx.larv.plugin.lang.LarvIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Represents the "Larv Project" template card in the New Project wizard.
 * Delegates project creation to {@link LarvModuleBuilder}.
 */
public class LarvProjectTemplate implements ProjectTemplate {

    @Override
    public @NotNull String getName() {
        return "Larv Project";
    }

    @Override
    public @Nullable String getDescription() {
        return "<html>Creates a new <b>Larv</b> project with a standard structure:<br>" +
                "<ul>" +
                "  <li><code>main.larv</code> – entry point</li>" +
                "  <li><code>utils.larv</code> – utility helpers</li>" +
                "  <li><code>README.md</code>  – project notes</li>" +
                "</ul>" +
                "</html>";
    }

    @Override
    public Icon getIcon() {
        return LarvIcons.FILE;
    }

    @Override
    public @NotNull AbstractModuleBuilder createModuleBuilder() {
        return new LarvModuleBuilder();
    }

    @Override
    public @Nullable ValidationInfo validateSettings() {
        return null; // validation handled inside LarvModuleBuilder's wizard step
    }
}