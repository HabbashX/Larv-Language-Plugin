package com.habbashx.larv.plugin.project;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.habbashx.larv.plugin.lang.LarvIcons;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LarvModuleBuilder extends ModuleBuilder {

    // ─────────────────────────────────────────────
    // Core project identity
    // ─────────────────────────────────────────────

    private String larvProjectName = "";
    private String artifactName = "";
    private String folderName = "";

    // NEW: project location from wizard
    private String projectLocation = "";

    // ─────────────────────────────────────────────
    // Build system
    // ─────────────────────────────────────────────

    private BuildTool buildTool = BuildTool.NONE;

    // ─────────────────────────────────────────────
    // Starter files
    // ─────────────────────────────────────────────

    private boolean generateMainLarv = true;
    private boolean generateReadme = true;

    // ─────────────────────────────────────────────
    // Module setup
    // ─────────────────────────────────────────────

    @Override
    public String getBuilderId() {
        return "larv.module.builder";
    }

    @Override
    public String getPresentableName() {
        return "Larv";
    }

    @Override
    public String getDescription() {
        return "Creates a new Larv scripting language project.";
    }

    @Override
    public Icon getNodeIcon() {
        return LarvIcons.FILE;
    }

    @Override
    public ModuleType<?> getModuleType() {
        return StdModuleTypes.JAVA;
    }

    @Override
    public @Nullable ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
        return new LarvProjectSettingsStep(this);
    }

    // ─────────────────────────────────────────────
    // Project generation
    // ─────────────────────────────────────────────

    @Override
    public void setupRootModel(@NotNull ModifiableRootModel rootModel) throws ConfigurationException {
        super.setupRootModel(rootModel);

        String rootPath = getContentEntryPath();
        if (rootPath == null) return;

        Path projectRoot = Paths.get(rootPath);

        try {

            if (generateMainLarv) {
                writeFile(projectRoot, "main.larv", buildMainLarv());
            }

            if (generateReadme) {
                writeFile(projectRoot, "README.md", buildReadme());
            }

            if (buildTool == BuildTool.LARV_ROCKET) {
                Path configDir = projectRoot.resolve("configuration");
                writeFile(configDir, "config.xml", buildConfigXml());
            }

            refreshVfs(rootPath);

        } catch (IOException e) {
            throw new ConfigurationException(
                    "Larv project generation failed: " + e.getMessage()
            );
        }
    }

    // ─────────────────────────────────────────────
    // File generators
    // ─────────────────────────────────────────────

    @Contract(pure = true)
    private @NotNull String buildMainLarv() {
        String name = safe(larvProjectName, "MyProject");

        return ""
                + "func main() {\n"
                + "    print(\"Hello from " + name + "!\")\n"
                + "}\n\n"
                + "main()\n";
    }

    @Contract(pure = true)
    private @NotNull String buildReadme() {

        String name = safe(larvProjectName, "My Larv Project");
        String group = safe(folderName, "com.example");
        String art = safe(artifactName, "my-project");

        return ""
                + "# " + name + "\n\n"
                + "> Larv project\n\n"
                + "## Info\n\n"
                + "| Key | Value |\n"
                + "|---|---|\n"
                + "| Group | `" + group + "` |\n"
                + "| Artifact | `" + art + "` |\n"
                + "| Build | `" + buildTool.name() + "` |\n\n"
                + "## Run\n\n"
                + "```bash\n"
                + "larv run main.larv\n"
                + "```\n";
    }

    @Contract(pure = true)
    private @NotNull String buildConfigXml() {

        String name = safe(larvProjectName, "MyProject");
        String group = safe(folderName, "com.example");
        String art = safe(artifactName, "my-project");

        return ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<lrocket-config>\n"
                + "  <project>\n"
                + "    <name>" + name + "</name>\n"
                + "    <groupId>" + group + "</groupId>\n"
                + "    <artifactId>" + art + "</artifactId>\n"
                + "    <version>1.0.0</version>\n"
                + "  </project>\n"
                + "  <build>\n"
                + "    <sourceDirectory>src</sourceDirectory>\n"
                + "    <outputDirectory>out</outputDirectory>\n"
                + "  </build>\n"
                + "  <runner>\n"
                + "    <mainFile>main.larv</mainFile>\n"
                + "    <debug>false</debug>\n"
                + "  </runner>\n"
                + "</lrocket-config>\n";
    }

    // ─────────────────────────────────────────────
    // File system utilities
    // ─────────────────────────────────────────────

    private static void writeFile(Path dir, String fileName, @NotNull String content) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String safe(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }

    private void refreshVfs(String rootPath) {
        VirtualFile vFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(rootPath);

        if (vFile != null) {
            vFile.refresh(true, true);
        }
    }

    // ─────────────────────────────────────────────
    // Getters / setters
    // ─────────────────────────────────────────────

    public String getLarvProjectName() { return larvProjectName; }
    public String getArtifactName() { return artifactName; }
    public String getFolderName() { return folderName; }
    public String getProjectLocation() { return projectLocation; }

    public BuildTool getBuildTool() { return buildTool; }

    public boolean isGenerateMainLarv() { return generateMainLarv; }
    public boolean isGenerateReadme() { return generateReadme; }

    public void setLarvProjectName(String v) { this.larvProjectName = v; }
    public void setArtifactName(String v) { this.artifactName = v; }
    public void setFolderName(String v) { this.folderName = v; }
    public void setProjectLocation(String v) { this.projectLocation = v; }

    public void setBuildTool(BuildTool v) { this.buildTool = v; }

    public void setGenerateMainLarv(boolean v) { this.generateMainLarv = v; }
    public void setGenerateReadme(boolean v) { this.generateReadme = v; }
}