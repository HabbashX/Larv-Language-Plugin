package com.habbashx.larv.plugin.completion;

import com.habbashx.larv.plugin.lang.LarvLanguage;
import com.habbashx.larv.plugin.lexer.LarvTokenTypes;
import com.habbashx.larv.plugin.parser.LarvElementTypes;
import com.habbashx.larv.plugin.registry.StdlibRegistry;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class LarvCompletionContributor extends CompletionContributor {

    private static final Logger LOG = Logger.getInstance(LarvCompletionContributor.class);

    public LarvCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(LarvLanguage.INSTANCE),
                new LarvCompletionProvider()
        );
    }

    private static final class LarvCompletionProvider extends CompletionProvider<CompletionParameters> {

        private static final Set<String> ALLOWED_PACKAGE_PREFIXES = Set.of(
                "java.", "org.apache.", "com.google.", "com.fasterxml.", "io.netty.",
                "org.slf4j.", "ch.qos.", "org.junit.", "org.mockito."
        );

        private boolean isAllowedPackage(@NotNull String fqn) {
            for (String prefix : ALLOWED_PACKAGE_PREFIXES) {
                if (fqn.startsWith(prefix)) return true;
            }
            return false;
        }

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {

            PsiElement position = parameters.getPosition();
            Project project = parameters.getPosition().getProject();

            // 2. Get the Document Manager
            PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);

            // 3. Force-fetch the CURRENT version of the file from the document
            // This is the "magic" fix for stale memory
            PsiFile file = docManager.getPsiFile(docManager.getDocument(parameters.getOriginalFile()));

            if (file == null) return;

            LOG.info("[Larv] addCompletions position=" + position.getClass().getSimpleName()
                    + " text='" + position.getText() + "' offset=" + position.getTextOffset()
                    + " file=" + file.getName());

            // ── include "..." → Java class search
            if (isAfterInclude(position)) {
                LOG.info("[Larv] -> include context, adding Java class completions");
                addJavaClassCompletions("", project, position, result);
                return;
            }
            String javaPrefix = getJavaClassStringPrefix(position);
            if (javaPrefix != null) {
                LOG.info("[Larv] -> java class prefix='" + javaPrefix + "'");
                addJavaClassCompletions(javaPrefix, project, position, result);
                return;
            }

            if (isInsideInvolve(position)) {
                LOG.info("[Larv] -> inside involve context");
                addInvolveParamCompletions(position, project, result);
                return;
            }

            String dotReceiver = getDotReceiver(position);
            if (dotReceiver != null) {
                LOG.info("[Larv] -> dot receiver='" + dotReceiver + "'");
                if ("this".equals(dotReceiver)) {
                    LOG.info("[Larv] -> this. members");
                    addThisMembers(position, file, result);
                    return;
                }
                if (addStdLibMethodsForReceiver(dotReceiver, file, result)) {
                    LOG.info("[Larv] -> stdlib methods for receiver='" + dotReceiver + "'");
                    return;
                }

                String includedFqn = resolveIncludeAlias(dotReceiver, file);
                if (includedFqn == null) includedFqn = resolveIncludeAliasFull(dotReceiver, file);
                if (includedFqn != null) {
                    LOG.info("[Larv] -> include alias resolved to '" + includedFqn + "'");
                    addJavaClassMethodCompletions(includedFqn, project, position, result);
                    return;
                }

                String className = resolveVariableClassFromContext(dotReceiver, position);
                LOG.info("[Larv] -> variable class for '" + dotReceiver + "' = '" + className + "'");
                if (className != null) {
                    if (!addClassMembersFromFile(className, file, result, project)) {
                        LOG.info("[Larv] -> class not in file, trying imports for '" + className + "'");
                        addClassMembersFromImportedFiles(className, file, project, result);
                    }
                }
                return;
            }

            // ── new | → class names
            if (isAfterNew(position)) {
                LOG.info("[Larv] -> after 'new', adding class names");
                addClassNames(file, result);
                addClassNamesFromImportedFiles(file, project, result);
                return;
            }

            // ── import "..." → stdlib + file paths
            if (isAfterImport(position)) {
                LOG.info("[Larv] -> after 'import', adding stdlib + file paths");
                addStdLibs(result);
                addLarvFileImports(position, result);
                return;
            }

            // ── var name : | or const NAME : | → built-in type suggestions
            if (isAfterVarTypeColon(position)) {
                LOG.info("[Larv] -> after type colon, adding built-in types");
                addBuiltinTypeCompletions(result);
                return;
            }

            // ── inside class body → class-context completions
            PsiElement enclosingClass = findEnclosingClassDecl(position);
            if (enclosingClass != null && isDirectlyInsideClassBody(position, enclosingClass)) {
                LOG.info("[Larv] -> inside class body '" + firstIdentifier(enclosingClass) + "'");
                addClassBodyCompletions(position, file, enclosingClass, project, result);
                return;
            }

            // ── general scope
            LOG.info("[Larv] -> general scope (keywords + builtins + file symbols + stdlib)");
            addKeywords(result);
            addBuiltins(result);
            addFileSymbols(file, result);
            addAllImportedLibMethods(file, result);
        }

        // ── Class body completions ────────────────────────────────────────────

        /**
         * Context-aware completions when cursor is directly inside a class body:
         * - Snippets for func / core func / override func
         * - Concrete override stubs for every overridable parent method
         * - var / const field snippets
         * - Inherited member names (for reference)
         */
        private void addClassBodyCompletions(@NotNull PsiElement position,
                                             @NotNull PsiFile file,
                                             @NotNull PsiElement classDecl,
                                             @NotNull Project project,
                                             @NotNull CompletionResultSet result) {
            String className    = firstIdentifier(classDecl);
            String superName    = extractSuperclassName(classDecl);

            result.addElement(keyword("override"));
            result.addElement(keyword("core"));
            result.addElement(keyword("sync"));
            result.addElement(keyword("defer"));

            // Snippet: plain func
            result.addElement(keywordSnippet("func", " name() {\n    \n}"));
            // Snippet: core func
            result.addElement(keywordSnippet("core func", " name() {\n    \n}"));
            // Snippet: override func (generic)
            result.addElement(keywordSnippet("override func", " name() {\n    \n}"));
            // Snippet: func with sync
            result.addElement(keywordSnippet("func : sync", " name() : sync {\n    \n}"));
            // Snippet: typed func with return type
            result.addElement(keywordSnippet("func (typed)", " name(param: type) -> type {\n    \n}"));
            // Snippet: defer func
            result.addElement(keywordSnippet("defer func", " name() {\n    \n}"));
            // Field snippets
            result.addElement(keywordSnippet("var", " fieldName = "));
            result.addElement(keywordSnippet("var (typed)", " fieldName : type = "));
            result.addElement(keywordSnippet("const", " FIELD_NAME = "));
            result.addElement(keywordSnippet("const (typed)", " FIELD_NAME : type = "));
            result.addElement(keywordSnippet("var : get,set", " fieldName : get, set"));
            result.addElement(keywordSnippet("var : get", " fieldName : get"));
            // Atomic/volatile field snippets
            result.addElement(keywordSnippet("atomic", "<type> fieldName = "));
            result.addElement(keywordSnippet("volatile", " fieldName = "));

            // Concrete override stubs from local superclass
            if (superName != null) {
                addOverrideStubsFromLocalClass(superName, file, result);
                addOverrideStubsFromImportedFiles(superName, file, project, result);
            }

            // Also offer existing own-class symbols for reference
            addFileSymbols(file, result);
        }

        /**
         * Adds  "override func methodName(params) { }"  stubs for every non-core
         * method found on the superclass (searched in the current file).
         */
        private void addOverrideStubsFromLocalClass(@NotNull String superClassName,
                                                    @NotNull PsiFile file,
                                                    @NotNull CompletionResultSet result) {
            PsiElement superDecl = findClassDecl(superClassName, file);
            if (superDecl == null) return;
            collectOverrideStubs(superDecl, result, new HashSet<>());
        }

        /**
         * Adds override stubs for superclass methods found in imported .larv files.
         */
        private void addOverrideStubsFromImportedFiles(@NotNull String superClassName,
                                                       @NotNull PsiFile file,
                                                       @NotNull Project project,
                                                       @NotNull CompletionResultSet result) {
            for (String importPath : collectImportedLarvPaths(file)) {
                LarvFileResolver.LarvFileInfo info = LarvFileResolver.resolveImport(
                        importPath, project, file.getVirtualFile());
                if (info == null) continue;
                for (LarvFileResolver.LarvClassInfo cls : info.classes()) {
                    if (!cls.name().equals(superClassName)) continue;
                    for (LarvFileResolver.LarvMethodInfo m : cls.methods()) {
                        if (m.isCore()) continue;  // core methods cannot be overridden
                        if (m.name().equals("init")) continue;
                        offerOverrideStub(m.name(), m.params(), result);
                    }
                    return;
                }
            }
        }

        /**
         * Walks a class PSI node and emits one "override func name(params) { }" stub
         * for each non-core, non-init method. Recurses up the superclass chain
         * within the same file.
         */
        private void collectOverrideStubs(@NotNull PsiElement classDecl,
                                          @NotNull CompletionResultSet result,
                                          @NotNull Set<String> seen) {
            for (PsiElement child : classDecl.getChildren()) {
                if (child.getNode() == null) continue;
                if (child.getNode().getElementType() == LarvElementTypes.FUNC_DECL) {
                    String name = firstIdentifier(child);
                    if (name == null || name.equals("init")) continue;
                    boolean isCore = hasFuncModifier(child, LarvTokenTypes.CORE);
                    if (isCore) continue;  // sealed — cannot be overridden
                    if (seen.add(name)) {
                        List<String> params = extractParamNames(child);
                        offerOverrideStub(name, params, result);
                    }
                }
            }
        }

        /** Emits a single "override func name(params) { }" completion item. */
        private void offerOverrideStub(@NotNull String name,
                                       @NotNull List<String> params,
                                       @NotNull CompletionResultSet result) {
            String paramStr  = String.join(", ", params);
            String insertText = "override func " + name + "(" + paramStr + ") {\n    \n}";
            result.addElement(
                    LookupElementBuilder.create("override func " + name)
                            .withPresentableText("override func " + name + "(" + paramStr + ")")
                            .withTypeText("override")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Method)
                            .bold()
                            .withInsertHandler((ctx, item) -> {
                                int start = ctx.getStartOffset();
                                int end   = ctx.getTailOffset();
                                ctx.getEditor().getDocument().replaceString(start, end, insertText);
                                // Place caret on the empty body line
                                ctx.getEditor().getCaretModel()
                                        .moveToOffset(start + insertText.indexOf("\n    \n") + 5);
                            })
            );
        }

        // ── this. member completion ───────────────────────────────────────────

        /**
         * Offers all fields and methods of the enclosing class (and its superclass chain)
         * when the user types  this.|
         */
        private void addThisMembers(@NotNull PsiElement position,
                                    @NotNull PsiFile file,
                                    @NotNull CompletionResultSet result) {
            PsiElement classDecl = findEnclosingClassDecl(position);
            if (classDecl == null) return;
            Set<String> seen = new HashSet<>();
            addClassMembersFromDecl(classDecl, file, result, seen, position.getProject());
        }

        /**
         * Adds fields + methods from a class PSI node, then walks up the superclass
         * chain within the same file (or imported files).
         */
        private void addClassMembersFromDecl(@NotNull PsiElement classDecl,
                                             @NotNull PsiFile file,
                                             @NotNull CompletionResultSet result,
                                             @NotNull Set<String> seen,
                                             @NotNull Project project) {
            for (PsiElement child : classDecl.getChildren()) {
                IElementType t = child.getNode().getElementType();
                if (t == LarvElementTypes.VAR_DECL || t == LarvElementTypes.CONST_DECL) {
                    String name = firstIdentifier(child);
                    if (name != null && seen.add(name)) {
                        result.addElement(LookupElementBuilder.create(name)
                                .withTypeText(t == LarvElementTypes.CONST_DECL ? "const field" : "field")
                                .withIcon(t == LarvElementTypes.CONST_DECL
                                        ? com.intellij.icons.AllIcons.Nodes.Constant
                                        : com.intellij.icons.AllIcons.Nodes.Field));
                    }
                }
                if (t == LarvElementTypes.FUNC_DECL) {
                    String name = firstIdentifier(child);
                    if (name != null && !name.equals("init") && seen.add(name)) {
                        String params = getParamNames(child);
                        boolean isCore = hasFuncModifier(child, LarvTokenTypes.CORE);
                        result.addElement(LookupElementBuilder.create(name)
                                .withTypeText(isCore ? "core method" : "method")
                                .withTailText("(" + params + ")", true)
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Method)
                                .bold()
                                .withInsertHandler((ctx, item) -> {
                                    int off = ctx.getTailOffset();
                                    ctx.getEditor().getDocument().insertString(off, "()");
                                    ctx.getEditor().getCaretModel().moveToOffset(off + 1);
                                }));
                    }
                }
            }

            // Walk superclass chain within same file
            String superName = extractSuperclassName(classDecl);
            if (superName != null) {
                PsiElement superDecl = findClassDecl(superName, file);
                if (superDecl != null) {
                    addClassMembersFromDecl(superDecl, file, result, seen, project);
                } else {
                    // Try imported files
                    addSuperMembersFromImports(superName, file, project, result, seen);
                }
            }
        }

        private void addSuperMembersFromImports(@NotNull String superClassName,
                                                @NotNull PsiFile file,
                                                @NotNull Project project,
                                                @NotNull CompletionResultSet result,
                                                @NotNull Set<String> seen) {
            for (String importPath : collectImportedLarvPaths(file)) {
                LarvFileResolver.LarvFileInfo info = LarvFileResolver.resolveImport(
                        importPath, project, file.getVirtualFile());
                if (info == null) continue;
                for (LarvFileResolver.LarvClassInfo cls : info.classes()) {
                    if (!cls.name().equals(superClassName)) continue;
                    for (LarvFileResolver.LarvMethodInfo m : cls.methods()) {
                        if (!seen.add(m.name())) continue;
                        String params = String.join(", ", m.params());
                        result.addElement(LookupElementBuilder.create(m.name())
                                .withTypeText((m.isCore() ? "core method" : "method") + " · " + info.fileName())
                                .withTailText("(" + params + ")", true)
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Method)
                                .bold()
                                .withInsertHandler((ctx, item) -> {
                                    int off = ctx.getTailOffset();
                                    ctx.getEditor().getDocument().insertString(off, "()");
                                    ctx.getEditor().getCaretModel().moveToOffset(off + 1);
                                }));
                    }
                    for (LarvFileResolver.LarvFieldInfo f : cls.fields()) {
                        if (!seen.add(f.name())) continue;
                        result.addElement(LookupElementBuilder.create(f.name())
                                .withTypeText((f.isConst() ? "const" : "var") + " · " + info.fileName())
                                .withIcon(f.isConst()
                                        ? com.intellij.icons.AllIcons.Nodes.Constant
                                        : com.intellij.icons.AllIcons.Nodes.Field));
                    }
                    return;
                }
            }
        }

        // ── dot-receiver member completion ────────────────────────────────────

        /**
         * Adds methods + fields for a class looked up by name in the current file or
         * imported files, walking the full superclass chain.
         * Returns true if the class was found.
         */
        private boolean addClassMembersFromFile(@NotNull String className,
                                                @NotNull PsiFile file,
                                                @NotNull CompletionResultSet result,
                                                @NotNull Project project) {
            PsiElement classDecl = findClassDecl(className, file);
            if (classDecl == null) return false;
            addClassMembersFromDecl(classDecl, file, result, new HashSet<>(), project);
            return true;
        }

        private void addClassMembersFromImportedFiles(@NotNull String className,
                                                      @NotNull PsiFile file,
                                                      @NotNull Project project,
                                                      @NotNull CompletionResultSet result) {
            for (String importPath : collectImportedLarvPaths(file)) {
                LarvFileResolver.LarvFileInfo info = LarvFileResolver.resolveImport(
                        importPath, project, file.getVirtualFile());
                if (info == null) continue;

                for (LarvFileResolver.LarvClassInfo cls : info.classes()) {
                    if (!cls.name().equals(className)) continue;
                    Set<String> seen = new HashSet<>();
                    emitClassInfoMembers(cls, info.fileName(), result, seen);

                    // Walk superclass chain via imports
                    String superName = cls.superclassName();
                    while (superName != null) {
                        LarvFileResolver.LarvClassInfo superCls = findClassInImports(
                                superName, file, project, info.virtualFile());
                        if (superCls == null) break;
                        emitClassInfoMembers(superCls, info.fileName(), result, seen);
                        superName = superCls.superclassName();
                    }
                    return;
                }
            }
        }

        @Nullable
        private LarvFileResolver.LarvClassInfo findClassInImports(@NotNull String className,
                                                                  @NotNull PsiFile file,
                                                                  @NotNull Project project,
                                                                  @Nullable VirtualFile excludeVf) {
            for (String importPath : collectImportedLarvPaths(file)) {
                LarvFileResolver.LarvFileInfo info = LarvFileResolver.resolveImport(
                        importPath, project, excludeVf);
                if (info == null) continue;
                for (LarvFileResolver.LarvClassInfo cls : info.classes()) {
                    if (cls.name().equals(className)) return cls;
                }
            }
            return null;
        }

        private void emitClassInfoMembers(@NotNull LarvFileResolver.LarvClassInfo cls,
                                          @NotNull String sourceName,
                                          @NotNull CompletionResultSet result,
                                          @NotNull Set<String> seen) {
            for (LarvFileResolver.LarvMethodInfo m : cls.methods()) {
                if (!seen.add(m.name())) continue;
                String params = String.join(", ", m.params());
                String typeLabel = (m.isCore() ? "core method" : "method") + " · " + sourceName;
                result.addElement(LookupElementBuilder.create(m.name())
                        .withTypeText(typeLabel)
                        .withTailText("(" + params + ")", true)
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Method)
                        .bold()
                        .withInsertHandler((ctx, item) -> {
                            int off = ctx.getTailOffset();
                            ctx.getEditor().getDocument().insertString(off, "()");
                            ctx.getEditor().getCaretModel().moveToOffset(off + 1);
                        }));
            }
            for (LarvFileResolver.LarvFieldInfo f : cls.fields()) {
                if (!seen.add(f.name())) continue;
                result.addElement(LookupElementBuilder.create(f.name())
                        .withTypeText((f.isConst() ? "const" : "var") + " · " + sourceName)
                        .withIcon(f.isConst()
                                ? com.intellij.icons.AllIcons.Nodes.Constant
                                : com.intellij.icons.AllIcons.Nodes.Field));
            }
        }


        private void addClassNames(@NotNull PsiFile file, @NotNull CompletionResultSet result) {
            collectClassNames(file, result, new HashSet<>());
        }

        private void collectClassNames(@NotNull PsiElement element,
                                       @NotNull CompletionResultSet result,
                                       @NotNull Set<String> seen) {
            for (PsiElement child : element.getChildren()) {
                if (child.getNode().getElementType() == LarvElementTypes.CLASS_DECL) {
                    String name = firstIdentifier(child);
                    if (name != null && seen.add(name)) {
                        String ctorParams = getConstructorParams(child);
                        result.addElement(LookupElementBuilder.create(name)
                                .withTypeText("class")
                                .withTailText("(" + ctorParams + ")", true)
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Class)
                                .bold()
                                .withInsertHandler((ctx, item) -> {
                                    int off = ctx.getTailOffset();
                                    ctx.getEditor().getDocument().insertString(off, "()");
                                    ctx.getEditor().getCaretModel().moveToOffset(off + 1);
                                }));
                    }
                }
                collectClassNames(child, result, seen);
            }
        }

        private void addClassNamesFromImportedFiles(@NotNull PsiFile file,
                                                    @NotNull Project project,
                                                    @NotNull CompletionResultSet result) {
            for (String importPath : collectImportedLarvPaths(file)) {
                LarvFileResolver.LarvFileInfo info = LarvFileResolver.resolveImport(
                        importPath, project, file.getVirtualFile());
                if (info == null) continue;

                for (LarvFileResolver.LarvClassInfo cls : info.classes()) {
                    String ctorParams = cls.constructor() != null
                            ? String.join(", ", cls.constructor().params()) : "";
                    result.addElement(LookupElementBuilder.create(cls.name())
                            .withTypeText("class · " + info.fileName())
                            .withTailText("(" + ctorParams + ")", true)
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Class)
                            .bold()
                            .withInsertHandler((ctx, item) -> {
                                int off = ctx.getTailOffset();
                                ctx.getEditor().getDocument().insertString(off, "()");
                                ctx.getEditor().getCaretModel().moveToOffset(off + 1);
                            }));
                }
            }
        }

        // ── PSI traversal helpers ─────────────────────────────────────────────

        /**
         * Returns the enclosing CLASS_DECL ancestor, or null if not inside any class.
         */
        @Nullable
        private PsiElement findEnclosingClassDecl(@NotNull PsiElement position) {
            PsiElement el = position.getParent();
            while (el != null && !(el instanceof PsiFile)) {
                if (el.getNode() != null
                        && el.getNode().getElementType() == LarvElementTypes.CLASS_DECL) {
                    return el;
                }
                el = el.getParent();
            }
            return null;
        }

        /**
         * Returns true if the position is directly inside the class body (not nested
         * inside a method body within the class).
         */
        private boolean isDirectlyInsideClassBody(@NotNull PsiElement position,
                                                  @NotNull PsiElement classDecl) {
            PsiElement el = position.getParent();
            while (el != null && !el.equals(classDecl)) {
                if (el.getNode() != null
                        && el.getNode().getElementType() == LarvElementTypes.FUNC_DECL) {
                    return false; // we're inside a method
                }
                el = el.getParent();
            }
            return true;
        }

        /** Reads the superclass name from a CLASS_DECL PSI node. */
        @Nullable
        private String extractSuperclassName(@NotNull PsiElement classDecl) {
            com.intellij.lang.ASTNode[] children = classDecl.getNode().getChildren(null);
            boolean nameSeen  = false;
            boolean colonSeen = false;
            for (com.intellij.lang.ASTNode n : children) {
                IElementType t = n.getElementType();
                if (t == com.intellij.psi.TokenType.WHITE_SPACE) continue;
                if (!nameSeen && t == LarvTokenTypes.IDENTIFIER) { nameSeen = true; continue; }
                if (nameSeen && !colonSeen && t == LarvTokenTypes.COLON) { colonSeen = true; continue; }
                if (colonSeen && t == LarvTokenTypes.IDENTIFIER) return n.getText();
                if (t == LarvTokenTypes.LBRACE) break;
            }
            return null;
        }

        /** Returns true if FUNC_DECL has a given modifier token as a direct child. */
        private boolean hasFuncModifier(@NotNull PsiElement funcDecl, IElementType modifier) {
            for (com.intellij.lang.ASTNode n : funcDecl.getNode().getChildren(null)) {
                if (n.getElementType() == modifier) return true;
            }
            return false;
        }

        /** Returns the constructor param names of a class, or "" if no init method. */
        @NotNull
        private String getConstructorParams(@NotNull PsiElement classDecl) {
            for (PsiElement child : classDecl.getChildren()) {
                if (child.getNode().getElementType() == LarvElementTypes.FUNC_DECL) {
                    String name = firstIdentifier(child);
                    if ("init".equals(name)) return getParamNames(child);
                }
            }
            return "";
        }

        @Nullable
        private String extractReturnType(@NotNull PsiElement funcDecl) {
            // Look for ARROW token followed by type identifier
            boolean arrowSeen = false;
            for (com.intellij.lang.ASTNode n : funcDecl.getNode().getChildren(null)) {
                if (n.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE) continue;
                if (n.getElementType() == LarvTokenTypes.ARROW) { arrowSeen = true; continue; }
                if (arrowSeen) {
                    if (n.getElementType() == LarvTokenTypes.IDENTIFIER) return n.getText();
                    if (LarvTokenTypes.BUILTIN_TYPES.contains(n.getElementType())) return n.getText();
                    break;
                }
            }
            return null;
        }

        @NotNull
        private List<String> extractParamNames(@NotNull PsiElement funcDecl) {
            for (PsiElement child : funcDecl.getChildren()) {
                if (child.getNode().getElementType() == LarvElementTypes.PARAM_LIST) {
                    List<String> params = new ArrayList<>();
                    boolean afterName = false;
                    String currentName = null;
                    for (com.intellij.lang.ASTNode n : child.getNode().getChildren(null)) {
                        IElementType t = n.getElementType();
                        if (t == LarvTokenTypes.IDENTIFIER) {
                            if (!afterName) {
                                currentName = n.getText();
                                afterName = true;
                            } else if (currentName != null) {
                                // This is the type after colon — append to param
                                params.add(currentName + ": " + n.getText());
                                currentName = null;
                                afterName = false;
                            }
                        } else if (t == LarvTokenTypes.COMMA) {
                            if (currentName != null) {
                                params.add(currentName);
                                currentName = null;
                            }
                            afterName = false;
                        } else if (t == LarvTokenTypes.COLON) {
                            afterName = true;
                        } else if (LarvTokenTypes.BUILTIN_TYPES.contains(t)) {
                            // Built-in type token (TYPE_INT, TYPE_STRING, etc.)
                            if (currentName != null) {
                                params.add(currentName + ": " + n.getText());
                                currentName = null;
                                afterName = false;
                            }
                        }
                    }
                    if (currentName != null) params.add(currentName);
                    return params;
                }
            }
            return List.of();
        }

        @NotNull
        private String getParamNames(@NotNull PsiElement funcDecl) {
            return String.join(", ", extractParamNames(funcDecl));
        }

        @Nullable
        private PsiElement findClassDecl(@NotNull String className, @NotNull PsiElement element) {
            for (PsiElement child : element.getChildren()) {
                if (child.getNode().getElementType() == LarvElementTypes.CLASS_DECL) {
                    if (className.equals(firstIdentifier(child))) return child;
                }
                PsiElement found = findClassDecl(className, child);
                if (found != null) return found;
            }
            return null;
        }

        private void addJavaClassCompletions(@NotNull String prefix,
                                             @NotNull Project project,
                                             @NotNull PsiElement position,
                                             @NotNull CompletionResultSet result) {
            GlobalSearchScope scope = buildScope(project, position);
            int lastDot = prefix.lastIndexOf('.');
            String packagePrefix = lastDot >= 0 ? prefix.substring(0, lastDot).toLowerCase() : "";
            String namePrefix    = lastDot >= 0 ? prefix.substring(lastDot + 1).toLowerCase() : prefix.toLowerCase();

            com.intellij.psi.search.searches.AllClassesSearch
                    .search(scope, project, simpleName ->
                            namePrefix.isEmpty() || simpleName.toLowerCase().startsWith(namePrefix))
                    .forEach(psiClass -> {
                        String fqn = psiClass.getQualifiedName();
                        if (fqn == null) return true;
                        if (fqn.contains("$")) return true;
                        if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) return true;
                        if (psiClass.isAnnotationType()) return true;
                        if (!isAllowedPackage(fqn)) return true;
                        if (!packagePrefix.isEmpty() && !fqn.toLowerCase().startsWith(packagePrefix)) return true;
                        try { if (psiClass.getContainingFile() == null) return true; }
                        catch (Exception e) { return true; }

                        String simpleName = psiClass.getName() != null ? psiClass.getName() : fqn;
                        result.addElement(
                                LookupElementBuilder.create(fqn)
                                        .withPresentableText(simpleName)
                                        .withTypeText(packageOf(fqn), true)
                                        .withIcon(iconForClass(psiClass))
                                        .withInsertHandler((ctx, item) -> {
                                            String doc  = ctx.getEditor().getDocument().getText();
                                            int start   = ctx.getStartOffset();
                                            int end     = ctx.getTailOffset();
                                            int qStart  = start, qEnd = end;
                                            if (qStart > 0 && doc.charAt(qStart - 1) == '"') qStart--;
                                            if (qEnd < doc.length() && doc.charAt(qEnd) == '"') qEnd++;
                                            ctx.getEditor().getDocument()
                                                    .replaceString(qStart, qEnd, "\"" + fqn + "\"");
                                            ctx.getEditor().getCaretModel()
                                                    .moveToOffset(qStart + fqn.length() + 2);
                                        }));
                        return true;
                    });
        }

        private void addJavaClassMethodCompletions(@NotNull String fqn,
                                                   @NotNull Project project,
                                                   @NotNull PsiElement position,
                                                   @NotNull CompletionResultSet result) {
            GlobalSearchScope scope = buildScope(project, position);
            PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqn, scope);
            if (psiClass == null)
                psiClass = JavaPsiFacade.getInstance(project)
                        .findClass(fqn, GlobalSearchScope.allScope(project));
            if (psiClass == null) return;

            Set<String> seen = new HashSet<>();
            PsiClass current = psiClass;
            while (current != null) {
                for (PsiMethod method : current.getMethods()) {
                    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) continue;
                    if (method.isConstructor()) continue;
                    String name = method.getName();
                    PsiParameterList paramList = method.getParameterList();
                    StringJoiner paramLabel = new StringJoiner(", ");
                    for (PsiParameter p : paramList.getParameters())
                        paramLabel.add(p.getType().getPresentableText() + " " + p.getName());
                    String params     = paramLabel.toString();
                    String returnType = method.getReturnType() != null
                            ? method.getReturnType().getPresentableText() : "void";
                    String dedupKey   = name + "(" + params + ")";
                    if (!seen.add(dedupKey)) continue;
                    result.addElement(
                            LookupElementBuilder.create(dedupKey)
                                    .withLookupString(name)
                                    .withPresentableText(name)
                                    .withTypeText(returnType)
                                    .withTailText("(" + params + ")", true)
                                    .withIcon(com.intellij.icons.AllIcons.Nodes.Method)
                                    .bold()
                                    .withInsertHandler((ctx, item) -> {
                                        int start = ctx.getStartOffset();
                                        int end   = ctx.getTailOffset();
                                        ctx.getEditor().getDocument().replaceString(start, end, name + "()");
                                        ctx.getEditor().getCaretModel().moveToOffset(start + name.length() + 1);
                                    }));
                }
                current = current.getSuperClass();
            }
        }

        private void addInvolveParamCompletions(@NotNull PsiElement position,
                                                @NotNull Project project,
                                                @NotNull CompletionResultSet result) {
            String fqn = extractFqnFromEnclosingInclude(position);
            if (fqn == null) return;
            GlobalSearchScope scope = buildScope(project, position);
            PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqn, scope);
            if (psiClass == null) return;

            PsiMethod[] constructors = psiClass.getConstructors();
            if (constructors.length == 0) {
                result.addElement(LookupElementBuilder.create("")
                        .withPresentableText("(no args)")
                        .withTypeText("default constructor")
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Method));
                return;
            }
            for (PsiMethod ctor : constructors) {
                if (!ctor.hasModifierProperty(PsiModifier.PUBLIC)) continue;
                PsiParameter[] params = ctor.getParameterList().getParameters();
                if (params.length == 0) {
                    result.addElement(LookupElementBuilder.create("")
                            .withPresentableText("(no args)")
                            .withTypeText("new " + psiClass.getName() + "()")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Method));
                    continue;
                }
                StringBuilder snippet = new StringBuilder();
                StringBuilder label   = new StringBuilder();
                for (int i = 0; i < params.length; i++) {
                    PsiParameter p = params[i];
                    String typeName  = p.getType().getPresentableText();
                    String paramName = p.getName();
                    String placeholder = defaultValueFor(typeName, paramName);
                    if (i > 0) { snippet.append(", "); label.append(", "); }
                    snippet.append(placeholder);
                    label.append(typeName).append(" ").append(paramName);
                }
                final String finalSnippet = snippet.toString();
                result.addElement(LookupElementBuilder.create(finalSnippet)
                        .withPresentableText(finalSnippet)
                        .withTypeText(label.toString())
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Method)
                        .withInsertHandler((ctx, item) -> {
                            int start = ctx.getStartOffset();
                            int end   = ctx.getTailOffset();
                            ctx.getEditor().getDocument().replaceString(start, end, finalSnippet);
                            ctx.getEditor().getCaretModel().moveToOffset(start + finalSnippet.length());
                        }));
            }
        }

        // ── include alias resolution ──────────────────────────────────────────

        @Nullable
        private String resolveIncludeAlias(@NotNull String alias, @NotNull PsiElement scope) {
            for (PsiElement child : scope.getChildren()) {
                IElementType t = child.getNode().getElementType();
                if (t == LarvElementTypes.INCLUDE_STMT) {
                    String fqn = extractFqnForAlias(alias, child);
                    if (fqn != null) return fqn;
                }
            }
            return null;
        }

        @Nullable
        private String resolveIncludeAliasFull(@NotNull String alias, @NotNull PsiElement scope) {
            for (PsiElement child : scope.getChildren()) {
                IElementType t = child.getNode().getElementType();
                if (t == LarvElementTypes.INCLUDE_STMT) {
                    String fqn = extractFqnForAlias(alias, child);
                    if (fqn != null) return fqn;
                }
                String found = resolveIncludeAliasFull(alias, child);
                if (found != null) return found;
            }
            return null;
        }

        @Nullable
        private String extractFqnForAlias(@NotNull String alias, @NotNull PsiElement includeStmt) {
            String foundAlias = null;
            String foundFqn   = null;
            boolean fromSeen  = false;
            com.intellij.lang.ASTNode node = includeStmt.getNode().getFirstChildNode();
            while (node != null) {
                IElementType nt = node.getElementType();
                if (nt == com.intellij.psi.TokenType.WHITE_SPACE || nt == LarvTokenTypes.INCLUDE) {
                    node = node.getTreeNext(); continue;
                }
                if (nt == LarvTokenTypes.FROM) { fromSeen = true; }
                else if (!fromSeen && foundAlias == null) {
                    String text = node.getText().trim();
                    if (!text.isEmpty()) foundAlias = text;
                } else if (fromSeen && foundFqn == null && nt == LarvTokenTypes.STRING) {
                    String text = node.getText();
                    if (text.length() >= 2) foundFqn = text.substring(1, text.length() - 1);
                }
                node = node.getTreeNext();
            }
            if (foundAlias == null || foundFqn == null) return null;
            return foundAlias.equals(alias) ? foundFqn : null;
        }

        @Nullable
        private String extractFqnFromEnclosingInclude(@NotNull PsiElement position) {
            PsiElement cur = position;
            while (cur != null && !(cur instanceof PsiFile)) {
                if (cur.getNode().getElementType() == LarvElementTypes.INCLUDE_STMT) {
                    boolean fromSeen = false;
                    com.intellij.lang.ASTNode node = cur.getNode().getFirstChildNode();
                    while (node != null) {
                        if (node.getElementType() == LarvTokenTypes.FROM) fromSeen = true;
                        else if (fromSeen && node.getElementType() == LarvTokenTypes.STRING) {
                            String text = node.getText();
                            if (text.length() >= 2) return text.substring(1, text.length() - 1);
                        }
                        node = node.getTreeNext();
                    }
                }
                cur = cur.getParent();
            }
            return null;
        }

        private boolean isAfterInclude(@NotNull PsiElement position) {
            PsiElement prev = prevMeaningful(position);
            return prev != null && prev.getNode().getElementType() == LarvTokenTypes.INCLUDE;
        }

        private boolean isAfterNew(@NotNull PsiElement position) {
            PsiElement prev = prevMeaningful(position);
            return prev != null && prev.getNode().getElementType() == LarvTokenTypes.NEW;
        }

        private boolean isAfterImport(@NotNull PsiElement position) {
            PsiElement prev = prevMeaningful(position);
            return prev != null && prev.getNode().getElementType() == LarvTokenTypes.IMPORT;
        }

        @Nullable
        private String getJavaClassStringPrefix(@NotNull PsiElement position) {
            PsiElement prev = prevMeaningful(position);
            if (prev == null) return null;
            IElementType prevType = prev.getNode().getElementType();
            PsiElement cur = position;
            boolean insideIncludeStmt = false;
            while (cur != null && !(cur instanceof PsiFile)) {
                if (cur.getNode().getElementType() == LarvElementTypes.INCLUDE_STMT) {
                    insideIncludeStmt = true; break;
                }
                cur = cur.getParent();
            }
            if (!insideIncludeStmt) return null;
            if (prevType == LarvTokenTypes.FROM) return "";
            if (prevType == LarvTokenTypes.STRING
                    || position.getNode().getElementType() == LarvTokenTypes.STRING) {
                String text = position.getText();
                if (text.startsWith("\"")) text = text.substring(1);
                text = text.replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, "")
                        .replace(CompletionUtil.DUMMY_IDENTIFIER, "");
                if (text.endsWith("\"")) text = text.substring(0, text.length() - 1);
                return text;
            }
            return null;
        }

        private boolean isInsideInvolve(@NotNull PsiElement position) {
            PsiElement cur = position;
            while (cur != null && !(cur instanceof PsiFile)) {
                if (cur.getNode().getElementType() == LarvElementTypes.INCLUDE_STMT) {
                    com.intellij.lang.ASTNode node = cur.getNode().getFirstChildNode();
                    while (node != null) {
                        if (node.getElementType() == LarvTokenTypes.INVOLVE) return true;
                        node = node.getTreeNext();
                    }
                }
                cur = cur.getParent();
            }
            return false;
        }

        @Nullable
        /**
         * Returns the identifier that appears immediately before a dot in the
         * source text, or {@code null} if the cursor is not in a  "receiver.|"
         * position.
         *
         * <p>Strategy: take the PSI file text up to the start of the current
         * position element (which is where the dummy identifier begins), strip
         * trailing whitespace, and check whether the last character is a dot.
         * We do NOT strip the dummy identifier from the substring because we
         * are already taking the text <em>before</em> the position element,
         * so the dummy identifier never appears in that slice.</p>
         */
        private String getDotReceiver(@NotNull PsiElement position) {
            try {
                String fileText = position.getContainingFile().getText();
                // textOffset = start of the completion dummy token
                // Everything before it is real source text
                int offset = position.getTextOffset();
                if (offset <= 0) return null;
                String before = fileText.substring(0, offset).stripTrailing();
                if (before.isEmpty() || before.charAt(before.length() - 1) != '.') return null;
                // Chop the dot and extract the identifier to its left
                before = before.substring(0, before.length() - 1).stripTrailing();
                int end = before.length(), start = end;
                while (start > 0 && (Character.isLetterOrDigit(before.charAt(start - 1))
                        || before.charAt(start - 1) == '_')) start--;
                if (start >= end) return null;
                return before.substring(start, end);
            } catch (Exception e) { return null; }
        }

        // ── Variable → class resolution ───────────────────────────────────────

        @Nullable
        private String resolveVariableClass(@NotNull String varName, @NotNull PsiElement scope) {
            for (PsiElement child : scope.getChildren()) {
                if (child.getNode() == null) continue;
                IElementType t = child.getNode().getElementType();
                if (t == LarvElementTypes.VAR_DECL || t == LarvElementTypes.CONST_DECL) {
                    String name = firstIdentifier(child);
                    if (varName.equals(name)) {
                        // 1. Check for 'new ClassName()' initialiser
                        String cls = findNewExprClass(child);
                        if (cls != null) return cls;
                        // 2. Check for type annotation: var c : MyClass
                        //    Built-in types (string, int ...) are TYPE_* tokens and don't
                        //    point to a class, so we only return IDENTIFIER tokens.
                        String annotatedType = findTypeAnnotationClass(child);
                        if (annotatedType != null) return annotatedType;
                    }
                }
                // Recurse into nested scopes (func bodies, blocks, etc.)
                // but do NOT recurse into a CLASS_DECL — class fields belong
                // to the class scope, not the enclosing file scope.
                if (t != LarvElementTypes.CLASS_DECL) {
                    String found = resolveVariableClass(varName, child);
                    if (found != null) return found;
                }
            }
            return null;
        }

        /**
         * Resolves a variable's class by walking up the PSI ancestor chain
         * from the current cursor position. This finds variables declared in
         * enclosing function bodies that the file-root scan misses.
         */
        @Nullable
        private String resolveVariableClassFromContext(@NotNull String varName,
                                                       @NotNull PsiElement position) {
            // First, try the full file scan (catches top-level and nested vars)
            String fromFile = resolveVariableClass(varName, position.getContainingFile());
            if (fromFile != null) return fromFile;
            // Walk up the ancestor chain and scan each enclosing scope
            PsiElement ancestor = position.getParent();
            while (ancestor != null && !(ancestor instanceof PsiFile)) {
                String found = resolveVariableClassInScope(varName, ancestor);
                if (found != null) return found;
                ancestor = ancestor.getParent();
            }
            return null;
        }

        @Nullable
        private String resolveVariableClassInScope(@NotNull String varName,
                                                   @NotNull PsiElement scope) {
            for (PsiElement child : scope.getChildren()) {
                if (child.getNode() == null) continue;
                IElementType t = child.getNode().getElementType();
                if (t == LarvElementTypes.VAR_DECL || t == LarvElementTypes.CONST_DECL) {
                    String name = firstIdentifier(child);
                    if (varName.equals(name)) {
                        String cls = findNewExprClass(child);
                        if (cls != null) return cls;
                        String annotatedType = findTypeAnnotationClass(child);
                        if (annotatedType != null) return annotatedType;
                    }
                }
            }
            return null;
        }

        /**
         * If the VAR_DECL/CONST_DECL has a TYPE_ANNOTATION child whose type token is
         * an IDENTIFIER (i.e. a user-defined class name, not a built-in primitive),
         * returns that class name. Returns null for built-in types.
         */
        @Nullable
        private String findTypeAnnotationClass(@NotNull PsiElement varDecl) {
            for (PsiElement child : varDecl.getChildren()) {
                if (child.getNode() == null) continue;
                if (child.getNode().getElementType() == LarvElementTypes.TYPE_ANNOTATION) {
                    for (com.intellij.lang.ASTNode n : child.getNode().getChildren(null)) {
                        // IDENTIFIER inside TYPE_ANNOTATION = user-defined class name
                        if (n.getElementType() == LarvTokenTypes.IDENTIFIER) return n.getText();
                    }
                    // TYPE_* token = built-in primitive, no class to resolve
                    return null;
                }
            }
            return null;
        }

        @Nullable
        private String findNewExprClass(@NotNull PsiElement element) {
            for (PsiElement child : element.getChildren()) {
                if (child.getNode().getElementType() == LarvElementTypes.NEW_EXPR)
                    return firstIdentifier(child);
                String found = findNewExprClass(child);
                if (found != null) return found;
            }
            return null;
        }

        private void addKeywords(@NotNull CompletionResultSet result) {
            for (String kw : SIMPLE_KEYWORDS) result.addElement(keyword(kw));
            result.addElement(keywordSnippet("if",      "(condition) {\n    \n}"));
            result.addElement(keywordSnippet("else",    " {\n    \n}"));
            result.addElement(keywordSnippet("while",   "(condition) {\n    \n}"));
            result.addElement(keywordSnippet("for",     " i in range(n) {\n    \n}"));
            result.addElement(keywordSnippet("func",    " name() {\n    \n}"));
            result.addElement(keywordSnippet("func (typed)", " name(param: type) -> type {\n    \n}"));
            result.addElement(keywordSnippet("sync func", " name() {\n    \n}"));
            result.addElement(keywordSnippet("defer func", " name() {\n    \n}"));
            result.addElement(keywordSnippet("class",   " Name {\n    \n}"));
            result.addElement(keywordSnippet("class : inherit", " Name : SuperName {\n    \n}"));
            result.addElement(keywordSnippet("module",  " Name {\n    \n}"));
            result.addElement(keywordSnippet("enum",    " Name { A, B, C }"));
            result.addElement(keywordSnippet("try",     " {\n    \n} catch (e) {\n    \n}"));
            result.addElement(keywordSnippet("switch",  " expr {\n    case value:\n        {}\n    default:\n        {}\n}"));
            result.addElement(keywordSnippet("include", " Alias from \"java.package.ClassName\" involve {arg}"));
            result.addElement(keywordSnippet("import",  " \"library\""));
            result.addElement(keywordSnippet("var",     " name = "));
            result.addElement(keywordSnippet("var (typed)", " name : type = "));
            result.addElement(keywordSnippet("const",   " NAME = "));
            result.addElement(keywordSnippet("const (typed)", " NAME : type = "));
            result.addElement(keywordSnippet("atomic",  "<type> name = "));
            result.addElement(keywordSnippet("volatile", " name = "));
            result.addElement(keywordSnippet("return",  " "));
            result.addElement(keywordSnippet("throw",   " "));
            result.addElement(keywordSnippet("core func",     " name() {\n    \n}"));
            result.addElement(keywordSnippet("override func", " name() {\n    \n}"));
            result.addElement(keywordSnippet("func : sync",   " name() : sync {\n    \n}"));
        }

        private boolean isAfterColon(@NotNull PsiElement position) {
            PsiElement prev = prevMeaningful(position);
            return prev != null && prev.getNode().getElementType() == LarvTokenTypes.COLON;
        }

        /**
         * Returns true when the cursor sits right after a COLON that belongs to a
         * VAR_DECL or CONST_DECL — meaning the user is typing the type annotation:
         *   var name : |
         *   const NAME : |
         *
         * Distinguishes this from:
         *   - field accessors  (var x = 3 : |get…)  — colon parent is FIELD_ACCESSOR
         *   - param type       (func f(a: |))         — colon parent is PARAM_LIST
         *   - ternary colon    (cond ? a : |)         — parent is expression context
         *   - class superclass (class Foo : |)        — parent is CLASS_DECL
         */
        private boolean isAfterVarTypeColon(@NotNull PsiElement position) {
            PsiElement prev = prevMeaningful(position);
            if (prev == null) return false;
            if (prev.getNode().getElementType() != LarvTokenTypes.COLON) return false;

            // Strategy 1: walk PSI ancestor chain from the colon upward
            PsiElement parent = prev.getParent();
            while (parent != null && !(parent instanceof PsiFile)) {
                if (parent.getNode() == null) { parent = parent.getParent(); continue; }
                IElementType pt = parent.getNode().getElementType();
                if (pt == LarvElementTypes.VAR_DECL || pt == LarvElementTypes.CONST_DECL) {
                    // Ensure no EQUAL token precedes this COLON in the declaration
                    boolean equalSeen = false;
                    com.intellij.lang.ASTNode n = parent.getNode().getFirstChildNode();
                    while (n != null) {
                        if (n.getElementType() == LarvTokenTypes.EQUAL) { equalSeen = true; break; }
                        if (n.getPsi() != null && n.getPsi().getTextOffset() >= prev.getTextOffset()) break;
                        n = n.getTreeNext();
                    }
                    return !equalSeen;
                }
                // Bail out if we enter a context that is definitely not a var-type annotation
                if (pt == LarvElementTypes.FIELD_ACCESSOR
                        || pt == LarvElementTypes.PARAM_LIST
                        || pt == LarvElementTypes.CLASS_DECL) return false;
                parent = parent.getParent();
            }

            // Strategy 2: raw-text heuristic — handles incomplete/error PSI nodes
            // during active editing (e.g. `var str : |` before the value is typed)
            try {
                String fileText = position.getContainingFile().getText();
                int offset = position.getTextOffset();
                int lineStart = offset - 1;
                while (lineStart > 0 && fileText.charAt(lineStart - 1) != '\n') lineStart--;
                String line = fileText.substring(lineStart, offset)
                        .replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, "")
                        .replace(CompletionUtil.DUMMY_IDENTIFIER, "")
                        .stripTrailing();
                // Line must end with ':'
                if (line.isEmpty() || line.charAt(line.length() - 1) != ':') return false;
                String beforeColon = line.substring(0, line.length() - 1).stripTrailing();
                // Must not contain '=' (that would be a field-accessor colon)
                if (beforeColon.contains("=")) return false;
                // Must match: (var|const) <identifier>
                return java.util.regex.Pattern
                        .compile("(?:^|\\s)(var|const)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*$")
                        .matcher(beforeColon).find();
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * Suggests the six built-in primitive types after a type-annotation colon:
         *   var name : |  →  string, int, bool, float, double, long
         *
         * Each suggestion also shows a hint in the tail text and places the cursor
         * after the type keyword ready to type ' = value'.
         */
        private void addBuiltinTypeCompletions(@NotNull CompletionResultSet result) {
            record TypeEntry(String name, String desc) {}
            List<TypeEntry> types = List.of(
                    new TypeEntry("string",  "text value"),
                    new TypeEntry("int",     "integer number"),
                    new TypeEntry("bool",    "true / false"),
                    new TypeEntry("float",   "decimal (32-bit)"),
                    new TypeEntry("double",  "decimal (64-bit)"),
                    new TypeEntry("long",    "large integer (64-bit)")
            );
            for (TypeEntry t : types) {
                result.addElement(
                        LookupElementBuilder.create(t.name())
                                .withTypeText(t.desc())
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Type)
                                .bold()
                                .withInsertHandler((ctx, item) -> {
                                    // After inserting the type, append ' = ' so the user
                                    // can immediately type the initialiser.
                                    int off = ctx.getTailOffset();
                                    ctx.getEditor().getDocument().insertString(off, " = ");
                                    ctx.getEditor().getCaretModel().moveToOffset(off + 3);
                                })
                );
            }
        }

        private void addStdLibs(@NotNull CompletionResultSet result) {
            Map<String, String> libDescriptions = new LinkedHashMap<>();
            libDescriptions.put("math",       "sqrt, pow, abs, floor, ceil, sin, cos, log, PI");
            libDescriptions.put("io",         "readFile, writeFile, appendFile, deleteFile, listDir");
            libDescriptions.put("string",     "strLen, strUpper, strLower, strTrim, strSplit, strReplace");
            libDescriptions.put("list",       "listAdd, listGet, listSize, listSort, listRemove");
            libDescriptions.put("map",        "mapSet, mapGet, mapHas, mapKeys, mapValues");
            libDescriptions.put("http",       "httpGet, httpPost, httpPut, httpDelete");
            libDescriptions.put("system",     "exit, getEnv, exec, sleep, clock");
            libDescriptions.put("date",       "now, formatDate, parseDate, dateDiff, addDays");
            libDescriptions.put("base64",     "base64Encode, base64Decode, urlEncode, urlDecode");
            libDescriptions.put("regex",      "compile, matches, find, replaceAll, split");
            libDescriptions.put("converter",  "toInt, toFloat, toString, toBool");
            libDescriptions.put("properties", "loadProp, getProp, setProp, saveProp");
            libDescriptions.put("json",       "jsonStringify, jsonParse, jsonGet, jsonHas");
            libDescriptions.put("jdbc",       "dbConnect, dbQuery, dbExecute, dbClose");
            libDescriptions.put("thread",     "spawn, threadSleep, channelNew, channelSend");
            libDescriptions.put("socket",     "connect, send, receive, close");
            libDescriptions.put("server",     "bind, accept, close");
            for (Map.Entry<String, String> entry : libDescriptions.entrySet()) {
                result.addElement(LookupElementBuilder.create(entry.getKey())
                        .withTypeText("stdlib")
                        .withTailText("  — " + entry.getValue(), true)
                        .bold()
                        .withInsertHandler((ctx, item) -> {
                            int start = ctx.getStartOffset(), end = ctx.getTailOffset();
                            ctx.getEditor().getDocument()
                                    .replaceString(start, end, "\"" + entry.getKey() + "\"");
                            ctx.getEditor().getCaretModel()
                                    .moveToOffset(start + entry.getKey().length() + 2);
                        }));
            }
        }

        private static final List<String> SIMPLE_KEYWORDS = List.of(
                "true", "false", "nil", "this", "new",
                "break", "continue", "in", "from", "involve",
                "as", "catch", "finally", "case", "default",
                "override", "core", "sync", "defer", "atomic", "volatile"
        );

        private static @NotNull LookupElement keyword(String kw) {
            return LookupElementBuilder.create(kw).bold().withTypeText("keyword");
        }

        private static @NotNull LookupElement keywordSnippet(String kw, String tail) {
            return LookupElementBuilder.create(kw).bold().withTypeText("keyword")
                    .withInsertHandler((ctx, item) -> {
                        int off = ctx.getTailOffset();
                        ctx.getEditor().getDocument().insertString(off, tail);
                        ctx.getEditor().getCaretModel().moveToOffset(off + tail.length());
                    });
        }

        private void addBuiltins(@NotNull CompletionResultSet result) {
            for (StdlibRegistry.StdMethod m : StdlibRegistry.LIBRARIES.get("core")) {
                result.addElement(LookupElementBuilder.create(m.name()).bold()
                        .withPresentableText(m.name() + m.signature())
                        .withTailText(" -> " + m.returnType(), true)
                        .withTypeText(m.description())
                        .withInsertHandler((ctx, item) -> {
                            int off = ctx.getTailOffset();
                            if (m.name().equals("print") || m.name().equals("printErr")) {
                                ctx.getEditor().getDocument().insertString(off, "(\"\")");
                                ctx.getEditor().getCaretModel().moveToOffset(off + 2);
                                return;
                            }
                            ctx.getEditor().getDocument().insertString(off, "()");
                            ctx.getEditor().getCaretModel().moveToOffset(off + 1);
                        }));
            }
        }

        private static LookupElement builtin(String name, String params, String desc) {
            return LookupElementBuilder.create(name).bold()
                    .withTailText(params, true).withTypeText(desc)
                    .withInsertHandler((ctx, item) -> {
                        int off = ctx.getTailOffset();
                        if (name.equals("print") || name.equals("printErr")) {
                            ctx.getEditor().getDocument().insertString(off, "(\"\")");
                            ctx.getEditor().getCaretModel().moveToOffset(off + 2);
                            return;
                        }
                        ctx.getEditor().getDocument().insertString(off, "()");
                        ctx.getEditor().getCaretModel().moveToOffset(off + 1);
                    });
        }

        // ── File-level symbol walk ────────────────────────────────────────────

        private void addFileSymbols(@NotNull PsiFile file, @NotNull CompletionResultSet result) {
            walkElement(file, result, new HashSet<>());
        }

        private void walkElement(@NotNull PsiElement element,
                                 @NotNull CompletionResultSet result,
                                 @NotNull Set<String> seen) {
            for (PsiElement child : element.getChildren()) {
                IElementType t = child.getNode().getElementType();
                String name = firstIdentifier(child);
                if (name != null && seen.add(name)) {
                    if (t == LarvElementTypes.FUNC_DECL) {
                        List<String> paramNames = extractParamNames(child);
                        boolean isCore = hasFuncModifier(child, LarvTokenTypes.CORE);
                        boolean isAsync = hasFuncModifier(child, LarvTokenTypes.ASYNC);
                        String paramStr = String.join(", ", paramNames);
                        String returnHint = extractReturnType(child);
                        String label = paramNames.isEmpty()
                                ? name + "()"
                                : name + "(" + paramStr + ")";
                        if (returnHint != null) label += " -> " + returnHint;
                        String typeLabel = (isCore ? "core " : "") + (isAsync ? "async " : "") + "func";
                        result.addElement(LookupElementBuilder.create(name)
                                .withPresentableText(label)
                                .withTypeText(typeLabel)
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Function)
                                .bold()
                                .withInsertHandler((ctx, item) -> {
                                    int off = ctx.getTailOffset();
                                    ctx.getEditor().getDocument().insertString(off, "()");
                                    if (!paramNames.isEmpty()) {
                                        ctx.getEditor().getCaretModel().moveToOffset(off + 1);
                                    } else {
                                        ctx.getEditor().getCaretModel().moveToOffset(off + 2);
                                    }
                                }));
                    }
                    else if (t == LarvElementTypes.CLASS_DECL)
                        result.addElement(LookupElementBuilder.create(name)
                                .withTypeText("class")
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Class));
                    else if (t == LarvElementTypes.MODULE_DECL)
                        result.addElement(LookupElementBuilder.create(name).withTypeText("module"));
                    else if (t == LarvElementTypes.ENUM_DECL)
                        result.addElement(LookupElementBuilder.create(name)
                                .withTypeText("enum")
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Enum));
                    else if (t == LarvElementTypes.VAR_DECL)
                        result.addElement(LookupElementBuilder.create(name)
                                .withTypeText("var")
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Variable));
                    else if (t == LarvElementTypes.CONST_DECL)
                        result.addElement(LookupElementBuilder.create(name)
                                .withTypeText("const")
                                .withIcon(com.intellij.icons.AllIcons.Nodes.Constant));
                    else seen.remove(name);
                }
                walkElement(child, result, seen);
            }
        }

        // ── Stdlib method completion ──────────────────────────────────────────

        private boolean addStdLibMethodsForReceiver(@NotNull String receiver,
                                                     @NotNull PsiFile file,
                                                     @NotNull CompletionResultSet result) {
            String libName = findImportedLib(receiver, file);
            if (libName == null) return false;
            List<StdlibRegistry.StdMethod> methods = StdlibRegistry.LIBRARIES.get(libName);
            if (methods == null) return false;
            for (StdlibRegistry.StdMethod m : methods) {
                result.addElement(LookupElementBuilder.create(m.name())
                        .withPresentableText(m.name() + m.signature())
                        .withTypeText(m.returnType() + " · " + libName)
                        .withTailText("  — " + m.description(), true)
                        .bold()
                        .withInsertHandler((ctx, item) -> {
                            int off = ctx.getTailOffset();
                            ctx.getEditor().getDocument().insertString(off, "()");
                            ctx.getEditor().getCaretModel().moveToOffset(off + 1);
                        }));
            }
            return true;
        }

        @Nullable
        private String findImportedLib(@NotNull String receiver, @NotNull PsiElement scope) {
            for (PsiElement child : scope.getChildren()) {
                IElementType t = child.getNode().getElementType();
                if (t == LarvElementTypes.IMPORT_STMT) {
                    String imported = firstStringLiteral(child);
                    if (imported != null && imported.equals(receiver)) return imported;
                }
                String found = findImportedLib(receiver, child);
                if (found != null) return found;
            }
            return null;
        }

        private void addAllImportedLibMethods(@NotNull PsiFile file,
                                               @NotNull CompletionResultSet result) {
            Set<String> importedLibs = collectImportedLibs(file);
            for (Map.Entry<String, List<StdlibRegistry.StdMethod>> entry : StdlibRegistry.LIBRARIES.entrySet()) {
                String libName = entry.getKey();
                if ("core".equals(libName)) continue; // builtins handled separately
                boolean alreadyImported = importedLibs.contains(libName);
                for (StdlibRegistry.StdMethod m : entry.getValue()) {
                    String tailSuffix = alreadyImported ? "" : "  [auto-import \"" + libName + "\"]";
                    result.addElement(LookupElementBuilder.create(m.name())
                            .withPresentableText(m.name() + m.signature())
                            .withTypeText(m.returnType() + " · " + libName)
                            .withTailText("  — " + m.description() + tailSuffix, true)
                            .bold()
                            .withInsertHandler((ctx, item) -> {
                                int off = ctx.getTailOffset();
                                ctx.getEditor().getDocument().insertString(off, "()");
                                ctx.getEditor().getCaretModel().moveToOffset(off + 1);
                                if (!alreadyImported) {
                                    String importLine = "import \"" + libName + "\"\n";
                                    String docText = ctx.getEditor().getDocument().getText();
                                    if (!docText.contains("import \"" + libName + "\""))
                                        ctx.getEditor().getDocument().insertString(0, importLine);
                                }
                            }));
                }
            }
        }

        @NotNull
        private Set<String> collectImportedLibs(@NotNull PsiElement scope) {
            Set<String> libs = new LinkedHashSet<>();
            collectImportedLibsInto(scope, libs);
            return libs;
        }

        private void collectImportedLibsInto(@NotNull PsiElement scope, @NotNull Set<String> libs) {
            com.intellij.lang.ASTNode node = scope.getNode();
            if (node == null) return;
            com.intellij.lang.ASTNode child = node.getFirstChildNode();
            while (child != null) {
                IElementType t = child.getElementType();
                if (t == LarvElementTypes.IMPORT_STMT) {
                    String imported = firstStringLiteral(child.getPsi());
                    if (imported != null && StdlibRegistry.LIBRARIES.containsKey(imported)) libs.add(imported);
                }
                collectImportedLibsInto(child.getPsi(), libs);
                child = child.getTreeNext();
            }
        }

        @NotNull
        private List<String> collectImportedLarvPaths(@NotNull PsiElement scope) {
            List<String> paths = new ArrayList<>();
            for (PsiElement child : scope.getChildren()) {
                if (child.getNode().getElementType() == LarvElementTypes.IMPORT_STMT) {
                    for (com.intellij.lang.ASTNode n : child.getNode().getChildren(null)) {
                        if (n.getElementType() == LarvTokenTypes.STRING) {
                            String text = n.getText();
                            if (text.length() >= 2) {
                                String path = text.substring(1, text.length() - 1);
                                // Accept explicit .larv paths: import "AnotherClass.larv"
                                // AND bare names with no extension: import "AnotherClass"
                                // Exclude stdlib names (math, io, string, …) — they have no dot
                                // but are stdlib identifiers, not file paths.
                                boolean isExplicitLarv = path.endsWith(".larv");
                                boolean isBareFileName = !path.contains(".")
                                        && !StdlibRegistry.LIBRARIES.containsKey(path);
                                if (isExplicitLarv || isBareFileName) {
                                    paths.add(path);
                                }
                            }
                        }
                    }
                }
            }
            return paths;
        }

        private void addLarvFileImports(@NotNull PsiElement position,
                                        @NotNull CompletionResultSet result) {
            Project project = position.getProject();
            VirtualFile current = position.getContainingFile() != null
                    ? position.getContainingFile().getVirtualFile() : null;
            for (LarvFileResolver.LarvFileInfo info : LarvFileResolver.getAllLarvFiles(project, current)) {
                String path = info.relativePath();
                result.addElement(
                        LookupElementBuilder.create(path)
                                .withPresentableText(info.fileName())
                                .withTypeText(path, true)
                                .withIcon(com.intellij.icons.AllIcons.FileTypes.Text)
                                .withInsertHandler((ctx, item) -> {
                                    String doc = ctx.getEditor().getDocument().getText();
                                    int start = ctx.getStartOffset(), end = ctx.getTailOffset();
                                    int qStart = start, qEnd = end;
                                    if (qStart > 0 && doc.charAt(qStart - 1) == '"') qStart--;
                                    if (qEnd < doc.length() && doc.charAt(qEnd) == '"') qEnd++;
                                    ctx.getEditor().getDocument()
                                            .replaceString(qStart, qEnd, "\"" + path + "\"");
                                    ctx.getEditor().getCaretModel()
                                            .moveToOffset(qStart + path.length() + 2);
                                }));
            }
        }

        // ── Shared PSI helpers ────────────────────────────────────────────────

        @Nullable
        private static String firstIdentifier(@NotNull PsiElement element) {
            // Pass 1: composite PSI children (works for most nodes)
            for (PsiElement child : element.getChildren()) {
                if (child.getNode() != null
                        && child.getNode().getElementType() == LarvTokenTypes.IDENTIFIER)
                    return child.getText();
            }
            // Pass 2: raw AST leaf nodes — needed when IDENTIFIER is a direct leaf
            // (e.g. inside NEW_EXPR, FUNC_DECL, VAR_DECL) and doesn't appear as a
            // composite PSI child from getChildren().
            for (com.intellij.lang.ASTNode n : element.getNode().getChildren(null)) {
                if (n.getElementType() == LarvTokenTypes.IDENTIFIER) return n.getText();
            }
            return null;
        }

        @Nullable
        private static String firstStringLiteral(@NotNull PsiElement element) {
            for (PsiElement child : element.getChildren()) {
                if (child.getNode().getElementType() == LarvTokenTypes.STRING) {
                    String text = child.getText();
                    if (text.length() >= 2) return text.substring(1, text.length() - 1);
                }
            }
            return null;
        }

        @Nullable
        private PsiElement prevMeaningful(@NotNull PsiElement e) {
            PsiElement prev = e.getPrevSibling();
            while (prev instanceof PsiWhiteSpace) prev = prev.getPrevSibling();
            if (prev != null) return prev;
            PsiElement parent = e.getParent();
            if (parent == null || parent instanceof PsiFile) return null;
            return prevMeaningful(parent);
        }

        @Nullable
        private PsiElement nextMeaningful(@NotNull PsiElement e) {
            PsiElement next = e.getNextSibling();
            while (next instanceof PsiWhiteSpace) next = next.getNextSibling();
            return next;
        }

        // ── Scope / icon helpers ──────────────────────────────────────────────

        @NotNull
        private GlobalSearchScope buildScope(@NotNull Project project, @NotNull PsiElement position) {
            Module module = ModuleUtilCore.findModuleForPsiElement(position);
            return module != null
                    ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
                    : GlobalSearchScope.allScope(project);
        }

        @NotNull
        private javax.swing.Icon iconForClass(@NotNull PsiClass psiClass) {
            if (psiClass.isInterface()) return com.intellij.icons.AllIcons.Nodes.Interface;
            if (psiClass.isEnum())      return com.intellij.icons.AllIcons.Nodes.Enum;
            return com.intellij.icons.AllIcons.Nodes.Class;
        }

        @NotNull
        private String packageOf(@NotNull String fqn) {
            int dot = fqn.lastIndexOf('.');
            return dot > 0 ? fqn.substring(0, dot) : "";
        }

        // ── Constructor default value hints ───────────────────────────────────

        @NotNull
        private String defaultValueFor(@NotNull String typeName, @NotNull String paramName) {
            String lowerName = paramName.toLowerCase();
            String lowerType = typeName.toLowerCase();
            if (typeName.equals("InputStream"))  return "System.in";
            if (typeName.equals("OutputStream") || typeName.equals("PrintStream")) return "System.out";
            if (typeName.endsWith("Reader")) return lowerName.contains("file") ? "new FileReader(\"path\")" : "new StringReader(\"\")";
            if (typeName.endsWith("Writer")) return "new FileWriter(\"path\")";
            if (typeName.equals("String")) {
                if (lowerName.contains("path") || lowerName.contains("file")) return "\"path\"";
                if (lowerName.contains("url")  || lowerName.contains("uri"))  return "\"https://\"";
                if (lowerName.contains("host")) return "\"localhost\"";
                return "\"\"";
            }
            if (lowerType.equals("int") || lowerType.equals("integer")) return lowerName.contains("port") ? "8080" : "0";
            if (lowerType.equals("long"))    return "0L";
            if (lowerType.equals("double") || lowerType.equals("float")) return "0.0";
            if (lowerType.equals("boolean")) return "false";
            if (lowerType.equals("char"))    return "'\\0'";
            if (lowerType.startsWith("list")) return "new ArrayList<>()";
            if (lowerType.startsWith("map"))  return "new HashMap<>()";
            if (lowerType.startsWith("set"))  return "new HashSet<>()";
            if (typeName.equals("File"))      return "new File(\"path\")";
            if (typeName.equals("Path"))      return "Paths.get(\"path\")";
            if (typeName.equals("Runnable"))  return "() -> {}";
            return paramName;
        }

        // ── Stdlib registry ───────────────────────────────────────────────────

        private record StdMethod(String name, String params) {}

        private static final Map<String, List<StdMethod>> STDLIB_METHODS = new LinkedHashMap<>();

        static {
            STDLIB_METHODS.put("math", List.of(
                    new StdMethod("sqrt","x"), new StdMethod("pow","base, exp"),
                    new StdMethod("abs","x"), new StdMethod("floor","x"),
                    new StdMethod("ceil","x"), new StdMethod("round","x"),
                    new StdMethod("max","a, b"), new StdMethod("min","a, b"),
                    new StdMethod("log","x"), new StdMethod("log10","x"),
                    new StdMethod("sin","x"), new StdMethod("cos","x"), new StdMethod("tan","x"),
                    new StdMethod("asin","x"), new StdMethod("acos","x"), new StdMethod("atan","x"),
                    new StdMethod("atan2","y, x"), new StdMethod("toRadians","degrees"),
                    new StdMethod("toDegrees","radians"), new StdMethod("random",""),
                    new StdMethod("randomInt","min, max"), new StdMethod("clamp","value, min, max"),
                    new StdMethod("sign","x"), new StdMethod("pi",""), new StdMethod("e",""),
                    new StdMethod("isNaN","x"), new StdMethod("isInfinite","x"), new StdMethod("toInt","x")));
            STDLIB_METHODS.put("io", List.of(
                    new StdMethod("readFile","path"), new StdMethod("writeFile","path, content"),
                    new StdMethod("appendFile","path, content"), new StdMethod("readLines","path"),
                    new StdMethod("readBytes","path"), new StdMethod("deleteFile","path"),
                    new StdMethod("fileExists","path"), new StdMethod("isDir","path"),
                    new StdMethod("listDir","path"), new StdMethod("makeDir","path"),
                    new StdMethod("copyFile","src, dest"), new StdMethod("moveFile","src, dest"),
                    new StdMethod("fileSize","path"), new StdMethod("cwd",""), new StdMethod("absPath","path"),
                    new StdMethod("openWriter","path"), new StdMethod("writeLine","writer, line"),
                    new StdMethod("closeWriter","writer"), new StdMethod("openReader","path"),
                    new StdMethod("readLine","reader"), new StdMethod("closeReader","reader")));
            STDLIB_METHODS.put("string", List.of(
                    new StdMethod("strLen","s"), new StdMethod("strUpper","s"), new StdMethod("strLower","s"),
                    new StdMethod("strTrim","s"), new StdMethod("strTrimLeft","s"), new StdMethod("strTrimRight","s"),
                    new StdMethod("strContains","s, sub"), new StdMethod("strStartsWith","s, prefix"),
                    new StdMethod("strEndsWith","s, suffix"), new StdMethod("strIndexOf","s, sub"),
                    new StdMethod("strSlice","s, start, end"), new StdMethod("strReplace","s, target, replacement"),
                    new StdMethod("strReplaceAll","s, target, replacement"), new StdMethod("strSplit","s, delimiter"),
                    new StdMethod("strJoin","list, delimiter"), new StdMethod("strRepeat","s, times"),
                    new StdMethod("strReverse","s"), new StdMethod("strCharAt","s, index"),
                    new StdMethod("strToNumber","s"), new StdMethod("strFromNumber","n"),
                    new StdMethod("strIsEmpty","s"), new StdMethod("strPadLeft","s, length, padChar"),
                    new StdMethod("strPadRight","s, length, padChar"), new StdMethod("strChars","s"),
                    new StdMethod("strFormat","template, args")));
            STDLIB_METHODS.put("list", List.of(
                    new StdMethod("listNew",""), new StdMethod("listAdd","list, item"),
                    new StdMethod("listAddAt","list, index, item"), new StdMethod("listRemove","list, index"),
                    new StdMethod("listGet","list, index"), new StdMethod("listSet","list, index, value"),
                    new StdMethod("listSize","list"), new StdMethod("listContains","list, item"),
                    new StdMethod("listIndexOf","list, item"), new StdMethod("listSlice","list, start, end"),
                    new StdMethod("listReverse","list"), new StdMethod("listSort","list"),
                    new StdMethod("listConcat","list1, list2"), new StdMethod("listFlat","list"),
                    new StdMethod("listUnique","list"), new StdMethod("listFill","value, times"),
                    new StdMethod("listClear","list"), new StdMethod("listIsEmpty","list"),
                    new StdMethod("listFirst","list"), new StdMethod("listLast","list"),
                    new StdMethod("listPop","list"), new StdMethod("listShuffle","list")));
            STDLIB_METHODS.put("map", List.of(
                    new StdMethod("mapNew",""), new StdMethod("mapSet","map, key, value"),
                    new StdMethod("mapGet","map, key"), new StdMethod("mapHas","map, key"),
                    new StdMethod("mapRemove","map, key"), new StdMethod("mapSize","map"),
                    new StdMethod("mapKeys","map"), new StdMethod("mapValues","map"),
                    new StdMethod("mapClear","map"), new StdMethod("mapIsEmpty","map"),
                    new StdMethod("mapMerge","map1, map2"), new StdMethod("mapContainsValue","map, value"),
                    new StdMethod("mapToList","map")));
            STDLIB_METHODS.put("http", List.of(
                    new StdMethod("httpGet","url"), new StdMethod("httpPost","url, body"),
                    new StdMethod("httpPostJson","url, json"), new StdMethod("httpPut","url, body"),
                    new StdMethod("httpDelete","url"), new StdMethod("httpGetStatus","response"),
                    new StdMethod("httpGetBody","response")));
            STDLIB_METHODS.put("system", List.of(
                    new StdMethod("exit","code"), new StdMethod("getEnv","key"),
                    new StdMethod("clock",""), new StdMethod("nanoTime",""),
                    new StdMethod("sleep","ms"), new StdMethod("exec","command"),
                    new StdMethod("osName",""), new StdMethod("osArch",""),
                    new StdMethod("javaVersion",""), new StdMethod("freeMemory",""),
                    new StdMethod("totalMemory",""), new StdMethod("maxMemory",""),
                    new StdMethod("gc",""), new StdMethod("sysProperty","key"),
                    new StdMethod("userName",""), new StdMethod("userHome","")));
            STDLIB_METHODS.put("date", List.of(
                    new StdMethod("now",""), new StdMethod("today",""),
                    new StdMethod("year","date"), new StdMethod("month","date"),
                    new StdMethod("day","date"), new StdMethod("hour","date"),
                    new StdMethod("minute","date"), new StdMethod("second","date"),
                    new StdMethod("formatDate","date, pattern"), new StdMethod("parseDate","s, pattern"),
                    new StdMethod("dateDiff","date1, date2"), new StdMethod("addDays","date, n"),
                    new StdMethod("addMonths","date, n"), new StdMethod("dayOfWeek","date"),
                    new StdMethod("isLeapYear","year")));
            STDLIB_METHODS.put("base64", List.of(
                    new StdMethod("base64Encode","s"), new StdMethod("base64Decode","s"),
                    new StdMethod("urlEncode","s"), new StdMethod("urlDecode","s"),
                    new StdMethod("hexEncode","s"), new StdMethod("hexDecode","s")));
            STDLIB_METHODS.put("regex", List.of(
                    new StdMethod("compile","pattern"), new StdMethod("reset","regex, input"),
                    new StdMethod("matches","regex"), new StdMethod("find","regex"),
                    new StdMethod("group","regex, index"), new StdMethod("groupCount","regex"),
                    new StdMethod("start","regex"), new StdMethod("end","regex"),
                    new StdMethod("replaceFirst","regex, replacement"),
                    new StdMethod("replaceAll","regex, replacement"),
                    new StdMethod("split","regex, input"), new StdMethod("quote","s")));
            STDLIB_METHODS.put("json", List.of(
                    new StdMethod("jsonStringify","value"), new StdMethod("jsonPretty","value"),
                    new StdMethod("jsonParse","s"), new StdMethod("jsonGet","json, key"),
                    new StdMethod("jsonHas","json, key"), new StdMethod("jsonIsValid","s")));
            STDLIB_METHODS.put("jdbc", List.of(
                    new StdMethod("dbConnect","url, user, password"),
                    new StdMethod("dbConnectPg","host, port, db, user, password"),
                    new StdMethod("dbConnectSql","host, port, db, user, password"),
                    new StdMethod("dbConnectSqlite","path"), new StdMethod("dbClose","conn"),
                    new StdMethod("dbQuery","conn, sql"), new StdMethod("dbExecute","conn, sql"),
                    new StdMethod("dbInsert","conn, sql"), new StdMethod("dbUpdate","conn, sql"),
                    new StdMethod("dbDelete","conn, sql"), new StdMethod("dbBegin","conn"),
                    new StdMethod("dbCommit","conn"), new StdMethod("dbRollback","conn"),
                    new StdMethod("dbPrepare","conn, sql"), new StdMethod("dbTables","conn")));
            STDLIB_METHODS.put("thread", List.of(
                    new StdMethod("spawn","func"), new StdMethod("threadSleep","ms"),
                    new StdMethod("threadId",""), new StdMethod("threadName",""),
                    new StdMethod("threadCount",""), new StdMethod("cpuCount",""),
                    new StdMethod("threadIsAlive","thread"), new StdMethod("threadJoin","thread"),
                    new StdMethod("channelNew",""), new StdMethod("channelSend","channel, value"),
                    new StdMethod("channelRecv","channel"), new StdMethod("channelClose","channel")));
            STDLIB_METHODS.put("socket", List.of(
                    new StdMethod("connect","host, port"), new StdMethod("importSocket","socket"),
                    new StdMethod("send","socket, message"), new StdMethod("receive","socket"),
                    new StdMethod("writeBytes","socket, bytes"), new StdMethod("readBytes","socket, n"),
                    new StdMethod("setSoTimeout","socket, ms"), new StdMethod("setTcpNoDelay","socket, flag"),
                    new StdMethod("setKeepAlive","socket, flag"),
                    new StdMethod("getRemoteAddr","socket"), new StdMethod("close","socket")));
            STDLIB_METHODS.put("server", List.of(
                    new StdMethod("bind","port"), new StdMethod("accept","server"),
                    new StdMethod("setSoTimeout","server, ms"), new StdMethod("close","server"),
                    new StdMethod("getPort","server"), new StdMethod("isClosed","server")));
            STDLIB_METHODS.put("properties", List.of(
                    new StdMethod("loadProp","path"), new StdMethod("getProp","key"),
                    new StdMethod("getPropOr","key, default"), new StdMethod("setProp","key, value"),
                    new StdMethod("hasProp","key"), new StdMethod("removeProp","key"),
                    new StdMethod("getAllProps",""), new StdMethod("saveProps","path"),
                    new StdMethod("loadPropsMap","path")));
            STDLIB_METHODS.put("converter", List.of(
                    new StdMethod("toNumber","x"), new StdMethod("toString","x"),
                    new StdMethod("toBool","x"), new StdMethod("toInt","x"),
                    new StdMethod("toHex","x"), new StdMethod("toOctal","x"),
                    new StdMethod("toBinary","x"), new StdMethod("fromHex","s"),
                    new StdMethod("fromOctal","s"), new StdMethod("fromBinary","s"),
                    new StdMethod("toBytes","x"), new StdMethod("fromBytes","bytes"),
                    new StdMethod("typeOf","x")));
        }
    }
}