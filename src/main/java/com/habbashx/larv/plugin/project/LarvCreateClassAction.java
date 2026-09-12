package com.habbashx.larv.plugin.project;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.Messages;
import com.habbashx.larv.plugin.lang.LarvIcons;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LarvCreateClassAction extends AnAction {

    public LarvCreateClassAction() {
        super("Larv Type", "Create Larv class/enum/module", LarvIcons.FILE);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || vf == null) return;

        if (!vf.isDirectory()) {
            vf = vf.getParent();
        }

        if (vf == null || !vf.isDirectory()) return;

        VirtualFile dir = vf;

        LarvNewClassDialog dialog = new LarvNewClassDialog(project);
        if (!dialog.showAndGet()) return;

        String name = dialog.getNameValue();
        String type = dialog.getType();

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                VirtualFile file = dir.createChildData(this, name + ".larv");

                String content = buildSkeleton(name, type);
                file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));

            } catch (IOException ex) {
                Messages.showErrorDialog(
                        project,
                        "Failed to create file: " + ex.getMessage(),
                        "Larv"
                );
            }
        });
    }

    @Contract(pure = true)
    private static @NotNull String buildSkeleton(String name, String type) {

        return switch (type) {

            case "enum" ->
                    "enum " + name + " {\n" +
                            "    // values\n" +
                            "}\n";

            case "module" ->
                    "module " + name + " {\n" +
                            "\n" +
                            "    // module entry\n" +
                            "}\n";

            default ->
                    "class " + name + " {\n" +
                            "\n" +
                            "    func init() {\n" +
                            "    }\n" +
                            "\n" +
                            "}\n";
        };
    }
}