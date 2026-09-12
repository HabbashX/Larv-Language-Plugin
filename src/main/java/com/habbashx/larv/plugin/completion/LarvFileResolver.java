package com.habbashx.larv.plugin.completion;

import com.habbashx.larv.syntax.lexer.Lexer;
import com.habbashx.larv.syntax.lexer.Token;
import com.habbashx.larv.syntax.parser.Parser;
import com.habbashx.larv.plugin.lang.LarvFileType;
import com.habbashx.larv.syntax.parser.ast.statement.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Resolves and parses imported {@code .larv} files using the authoritative
 * Larv {@link Lexer} + {@link Parser} from {@code Larv-Language-Core}.
 *
 * <p>Reads raw source bytes of each {@code .larv} file, runs them through the
 * real language lexer/parser, then walks the typed AST to extract every method,
 * field, and constructor that the runtime would see — guaranteeing completion
 * data is always accurate.</p>
 */
public final class LarvFileResolver {

    private LarvFileResolver() {}


    public record LarvFieldInfo(String name, boolean isConst) {}

    public record LarvMethodInfo(String name, List<String> params, boolean isCore, boolean isOverride, boolean isAsync) {
        public LarvMethodInfo(String name, List<String> params) {
            this(name, params, false, false, false);
        }
        public LarvMethodInfo(String name, List<String> params, boolean isCore, boolean isOverride) {
            this(name, params, isCore, isOverride, false);
        }
        public @NotNull String signature() {
            return (isAsync ? "async " : "") + name + "(" + String.join(", ", params) + ")";
        }
    }

    public record LarvClassInfo(
            String name,
            @Nullable String superclassName,
            List<LarvMethodInfo> methods,
            List<LarvFieldInfo>  fields,
            @Nullable LarvMethodInfo constructor
    ) {
        /** Backward-compat constructor (no superclass). */
        public LarvClassInfo(String name, List<LarvMethodInfo> methods,
                             List<LarvFieldInfo> fields, @Nullable LarvMethodInfo constructor) {
            this(name, null, methods, fields, constructor);
        }
    }

    public record LarvFileInfo(
            String fileName,
            String relativePath,
            VirtualFile virtualFile,
            List<LarvClassInfo> classes
    ) {}


    /**
     * Returns {@link LarvFileInfo} for every {@code .larv} file in the project,
     * excluding {@code excludeFile} (the file currently being edited).
     */
    @NotNull
    public static List<LarvFileInfo> getAllLarvFiles(@NotNull Project project,
                                                     @Nullable VirtualFile excludeFile) {
        List<LarvFileInfo> result = new ArrayList<>();
        Collection<VirtualFile> vFiles = FileTypeIndex.getFiles(
                LarvFileType.INSTANCE, GlobalSearchScope.allScope(project));

        String projectBase = project.getBasePath();

        for (VirtualFile vf : vFiles) {
            if (excludeFile != null && vf.equals(excludeFile)) continue;

            String relativePath = projectBase != null
                    ? stripExt(toRelative(vf.getPath(), projectBase))
                    : stripExt(vf.getName());

            List<LarvClassInfo> classes = parseClasses(vf);
            result.add(new LarvFileInfo(vf.getName(), relativePath, vf, classes));
        }
        return result;
    }
    /**
     * Finds the VirtualFile for a given import path.
     */
    @Nullable
    public static VirtualFile findFile(@NotNull String importPath,
                                       @NotNull Project project,
                                       @Nullable VirtualFile contextFile) {

        // 1. Ensure the import path has the correct extension
        String fileName = importPath.endsWith(".larv") ? importPath : importPath + ".larv";

        // 2. Try relative to the current file first (fastest)
        if (contextFile != null && contextFile.getParent() != null) {
            VirtualFile relativeFile = contextFile.getParent().findFileByRelativePath(fileName);
            if (relativeFile != null && relativeFile.exists()) {
                return relativeFile;
            }
        }

        // 3. Extract just the file name (in case the import includes directories like "utils/Testing")
        String baseName = fileName.contains("/")
                ? fileName.substring(fileName.lastIndexOf('/') + 1)
                : fileName;

        // 4. Search globally across the entire project using IntelliJ's index
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
                baseName,
                GlobalSearchScope.allScope(project)
        );

        // 5. Match the result. If they imported "utils/Testing", ensure the path matches.
        for (VirtualFile vf : files) {
            if (vf.getPath().replace('\\', '/').endsWith(fileName)) {
                return vf;
            }
        }

        return null;
    }

    /**
     * Resolves the import and returns the LIVE in-memory PsiFile.
     * (This is the method called by LarvCompletionContributor)
     */
    @Nullable
    public static PsiFile resolveImportPsi(@NotNull String importPath,
                                           @NotNull Project project,
                                           @Nullable VirtualFile contextFile) {

        VirtualFile targetVf = findFile(importPath, project, contextFile);
        if (targetVf == null) return null;

        return PsiManager.getInstance(project).findFile(targetVf);
    }

    /**
     * Finds the {@link LarvFileInfo} that matches {@code importPath}.
     *
     * <p>Accepts all common import forms used in Larv source:</p>
     * <ul>
     *   <li>{@code import "Testing"}             — bare name, no extension</li>
     *   <li>{@code import "Testing.larv"}        — explicit extension</li>
     *   <li>{@code import "subdir/Testing"}      — relative path, no extension</li>
     *   <li>{@code import "subdir/Testing.larv"} — relative path with extension</li>
     * </ul>
     */
    @Nullable
    public static LarvFileInfo resolveImport(@NotNull String importPath,
                                             @NotNull Project project,
                                             @Nullable VirtualFile fromFile) {
        String normalised = importPath.replace("\\", "/").replace(".larv", "");
        for (LarvFileInfo info : getAllLarvFiles(project, fromFile)) {
            String rel  = info.relativePath();          // already stripped of .larv
            String base = stripExt(info.fileName());    // e.g. "Testing" from "Testing.larv"
            if (rel.equals(normalised)
                    || rel.endsWith("/" + normalised)
                    || base.equals(normalised)) {
                return info;
            }
        }
        return null;
    }


    /**
     * Reads {@code vf}, lexes + parses it with the real Larv parser, then
     * extracts every top-level class defined in that file.
     *
     * Returns an empty list (never {@code null}) if the file cannot be read or parsed.
     */
    @NotNull
    private static List<LarvClassInfo> parseClasses(@NotNull VirtualFile vf) {
        String source;
        try {
            Document doc = FileDocumentManager.getInstance().getDocument(vf);
            if (doc != null) {
                source = doc.getText();
            } else {
                source = new String(vf.contentsToByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return List.of();
        }
        List<Statement> statements;
        try {
            List<Token> tokens = new Lexer(source).tokenize();
            statements = new Parser(tokens).parse();
        } catch (Exception e) {
            // Syntax errors in the imported file — skip gracefully
            return List.of();
        }

        // 3. Walk top-level statements, collect ClassStatement and InterfaceStatement nodes
        List<LarvClassInfo> classes = new ArrayList<>();
        for (Statement stmt : statements) {
            if (stmt instanceof ClassStatement cs) {
                classes.add(buildClassInfo(cs));
            } else if (stmt instanceof InterfaceStatement is) {
                classes.add(buildInterfaceInfo(is));
            }
        }
        return classes;
    }

    /**
     * Converts a {@link ClassStatement} AST node into a {@link LarvClassInfo}
     * by walking its body for functions, vars, and consts.
     *
     * <p>The class body is a flat {@code List<Statement>} — the parser does NOT
     * wrap it in a {@link BlockStatement}. We iterate it directly.</p>
     */
    @NotNull
    private static LarvClassInfo buildClassInfo(@NotNull ClassStatement cs) {
        List<LarvMethodInfo> methods = new ArrayList<>();
        List<LarvFieldInfo>  fields  = new ArrayList<>();

        // Walk the class body directly — it is a flat list, not a BlockStatement
        for (Statement stmt : cs.body()) {
            collectMember(stmt, methods, fields);
        }

        // Separate the constructor (init) from regular methods
        LarvMethodInfo ctor = null;
        Iterator<LarvMethodInfo> it = methods.iterator();
        while (it.hasNext()) {
            LarvMethodInfo m = it.next();
            if ("init".equals(m.name())) {
                ctor = m;
                it.remove();
                break;
            }
        }

        return new LarvClassInfo(cs.name(), cs.superclassName(), methods, fields, ctor);
    }

    /**
     * Converts an {@link InterfaceStatement} AST node into a {@link LarvClassInfo}
     * by walking its body for method declarations.
     */
    @NotNull
    private static LarvClassInfo buildInterfaceInfo(@NotNull InterfaceStatement is) {
        List<LarvMethodInfo> methods = new ArrayList<>();
        List<LarvFieldInfo>  fields  = new ArrayList<>();

        for (FunctionStatement fs : is.methods()) {
            List<String> paramStrings = fs.params().stream()
                    .map(p -> (p.type() == null || p.type().equals("any"))
                            ? p.name()
                            : p.name() + ": " + p.type())
                    .toList();
            methods.add(new LarvMethodInfo(
                    fs.name(), paramStrings, false, false, fs.isAsync()));
        }

        return new LarvClassInfo(is.name(), null, methods, fields, null);
    }

    /**
     * Processes a single statement from a class body, adding it to the
     * appropriate list.
     *
     * <ul>
     *   <li>{@link FunctionStatement} → method entry</li>
     *   <li>{@link VarStatement}      → field entry; if it has a getter/setter
     *       accessor, synthetic methods {@code getX()} / {@code setX()} are also added</li>
     *   <li>{@link ConstStatement}    → const field; if it has a getter,
     *       a synthetic {@code getX()} method is also added</li>
     *   <li>{@link BlockStatement}    → recurse (defensive, some parsers wrap bodies)</li>
     * </ul>
     */
    private static void collectMember(@NotNull Statement stmt,
                                      @NotNull List<LarvMethodInfo> methods,
                                      @NotNull List<LarvFieldInfo>  fields) {
        switch (stmt) {
            case FunctionStatement fs -> {
                List<String> paramStrings = fs.params().stream()
                        .map(p -> (p.type() == null || p.type().equals("any"))
                                ? p.name()
                                : p.name() + ": " + p.type())
                        .toList();
                methods.add(new LarvMethodInfo(
                        fs.name(), paramStrings, fs.isCore(), fs.isOverride(), fs.isAsync()));
            }
            case VarStatement vs -> {
                fields.add(new LarvFieldInfo(vs.name(), false));
                // Synthesize getter / setter methods if declared with : get, set
                if (vs.hasGetter()) {
                    String getterName = "get" + capitalize(vs.name());
                    methods.add(new LarvMethodInfo(getterName, List.of()));
                }
                if (vs.hasSetter()) {
                    String setterName = "set" + capitalize(vs.name());
                    methods.add(new LarvMethodInfo(setterName, List.of("value")));
                }
            }
            case ConstStatement ks -> {
                fields.add(new LarvFieldInfo(ks.name(), true));
                if (ks.hasGetter()) {
                    String getterName = "get" + capitalize(ks.name());
                    methods.add(new LarvMethodInfo(getterName, List.of()));
                }
            }
            // Defensive: some parsers wrap body statements in a block
            case BlockStatement bs -> {
                for (Statement inner : bs.statements()) {
                    collectMember(inner, methods, fields);
                }
            }
            default -> { /* other statements carry no completion-relevant members */ }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @NotNull
    private static String stripExt(@NotNull String path) {
        if (path.endsWith(".larv")) return path.substring(0, path.length() - 5);
        return path;
    }

    @NotNull
    private static String toRelative(@NotNull String fullPath, @NotNull String base) {
        if (fullPath.startsWith(base)) {
            String rel = fullPath.substring(base.length());
            if (!rel.isEmpty() && (rel.charAt(0) == '/' || rel.charAt(0) == '\\'))
                rel = rel.substring(1);
            return rel;
        }
        return fullPath;
    }

    @NotNull
    private static String capitalize(@NotNull String name) {
        if (name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}