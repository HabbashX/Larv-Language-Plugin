package com.habbashx.larv.plugin.project;

import com.habbashx.larv.plugin.lang.LarvIcons;
import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LarvFileIconProvider implements FileIconProvider {

    @Override
    public Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {

        if (!file.getName().endsWith(".larv")) return null;

        String text = file.getName();

        if (text.contains("module")) return LarvIcons.LARV_MODULE;
        if (text.contains("enum")) return LarvIcons.LARV_ENUM;

        return LarvIcons.FILE;
    }
}