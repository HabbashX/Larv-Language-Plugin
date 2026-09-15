package com.habbashx.larv.plugin.inspection;

import com.habbashx.larv.plugin.lexer.LarvTokenTypes;
import com.habbashx.larv.plugin.parser.LarvElementTypes;
import com.habbashx.larv.plugin.registry.StdlibRegistry;
import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class LarvInspection extends LocalInspectionTool {

    private static final Logger LOG = Logger.getInstance(LarvInspection.class);

    @Override public @NotNull String getDisplayName()      { return "Larv code inspection"; }
    @Override public @NotNull String getGroupDisplayName() { return "Larv"; }
    @Override public @NotNull String getShortName()        { return "LarvInspection"; }
    @Override public boolean isEnabledByDefault()          { return true; }

    @Override
    public @Nullable ProblemDescriptor @NotNull [] checkFile(
            @NotNull PsiFile file,
            @NotNull InspectionManager manager,
            boolean isOnTheFly) {

        System.out.println("[Larv Inspection] >>> checkFile ENTERED for: " + file.getName());
        try {
        System.out.println("[Larv Inspection] checkFile called with file: " + file.getClass().getName());
        List<ProblemDescriptor>  problems       = new ArrayList<>();
        Map<String, PsiElement>  declared       = new LinkedHashMap<>();
        Set<String>              used           = new HashSet<>();
        Set<String>              usedLibs       = new HashSet<>();
        Set<String>              importedLibs   = new LinkedHashSet<>();
        Map<String, PsiElement>  importElements = new LinkedHashMap<>();
        Map<String, PsiElement>  includeElements = new LinkedHashMap<>();
        Map<String, String>      varToClass     = new LinkedHashMap<>();

        // className -> Set<methodName>  (includes inherited methods after resolution)
        Map<String, Set<String>> classToMethods = new LinkedHashMap<>();

        // className -> Set<methodName>  (own-only methods, never expanded by inheritance)
        Map<String, Set<String>> classOwnMethods = new LinkedHashMap<>();

        // className -> superclassName (null if no superclass)
        Map<String, String>      classHierarchy = new LinkedHashMap<>();

        // className -> Set<core method names>
        Map<String, Set<String>> classCoreMethod = new LinkedHashMap<>();

        // className -> Map<methodName, paramCount>  (includes inherited after resolution)
        Map<String, Map<String, Integer>> classMethodArity = new LinkedHashMap<>();

        Map<String, Integer>     funcArity      = new LinkedHashMap<>();
        Set<String>              constNames     = new LinkedHashSet<>();

        collectImportedLibs(file, importedLibs, importElements);
        collectVarToClass(file, varToClass);
        collectClassInfo(file, classToMethods, classOwnMethods, classHierarchy, classCoreMethod, classMethodArity);
        resolveInheritedMethods(classToMethods, classHierarchy, classCoreMethod);
        resolveInheritedMethodArity(classMethodArity, classHierarchy);
        collectFuncArity(file, funcArity);
        collectIncludes(file, includeElements);

        // Collect names of classes that come from other files (include aliases + import stems).
        // These are external and we cannot inspect their bodies, so they must be treated as
        // known classes to avoid false "Superclass not defined" errors.
        Set<String> externalClassNames = collectExternalClassNames(file);

        LOG.info("[Larv Inspection] Checking file: " + file.getName()
                + " classes=" + classToMethods.keySet()
                + " varToClass=" + varToClass
                + " funcArity=" + funcArity);

        walkForInspection(file, manager, problems, declared, used, usedLibs,
                importedLibs, varToClass, classToMethods, classOwnMethods, classHierarchy,
                classCoreMethod, classMethodArity, funcArity, constNames, externalClassNames, new ArrayDeque<>(), isOnTheFly);

        // Unused includes
        for (Map.Entry<String, PsiElement> entry : includeElements.entrySet()) {
            if (!used.contains(entry.getKey())) {
                problems.add(manager.createProblemDescriptor(
                        entry.getValue(),
                        "Include alias '" + entry.getKey() + "' is never used",
                        new RemoveIncludeFix(),
                        ProblemHighlightType.WARNING,
                        isOnTheFly));
            }
        }

        return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);

        } catch (Exception e) {
            System.out.println("[Larv Inspection] CRASHED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            LOG.error("[Larv Inspection] CRASHED in checkFile: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            return ProblemDescriptor.EMPTY_ARRAY;
        }
    }

    // ── Main walker ───────────────────────────────────────────────────────────

    private void walkForInspection(@NotNull PsiElement element,
                                   @NotNull InspectionManager manager,
                                   @NotNull List<ProblemDescriptor> problems,
                                   @NotNull Map<String, PsiElement> declared,
                                   @NotNull Set<String> used,
                                   @NotNull Set<String> usedLibs,
                                   @NotNull Set<String> importedLibs,
                                   @NotNull Map<String, String> varToClass,
                                   @NotNull Map<String, Set<String>> classToMethods,
                                   @NotNull Map<String, Set<String>> classOwnMethods,
                                   @NotNull Map<String, String> classHierarchy,
                                   @NotNull Map<String, Set<String>> classCoreMethod,
                                   @NotNull Map<String, Map<String, Integer>> classMethodArity,
                                   @NotNull Map<String, Integer> funcArity,
                                   @NotNull Set<String> constNames,
                                   @NotNull Set<String> externalClassNames,
                                   @NotNull Deque<Set<String>> scopeStack,
                                   boolean onTheFly) {

        if (element.getNode() == null) return;
        // Skip empty or error-recovery nodes produced while user is actively typing
        if (element.getTextLength() == 0) return;
        IElementType type = element.getNode().getElementType();
        if (type == com.intellij.psi.TokenType.ERROR_ELEMENT) return;

        // ── var / const declarations ──────────────────────────────────────────
        if (type == LarvElementTypes.VAR_DECL || type == LarvElementTypes.CONST_DECL) {
            String name = firstIdentifierText(element);
            if (name != null) {

                if (isInsideClass(element)) {
                    if (type == LarvElementTypes.CONST_DECL) constNames.add(name);
                    for (PsiElement child : element.getChildren()) {
                        walkForInspection(child, manager, problems, declared, used, usedLibs,
                                importedLibs, varToClass, classToMethods, classOwnMethods, classHierarchy,
                                classCoreMethod, classMethodArity, funcArity, constNames, externalClassNames, scopeStack, onTheFly);
                    }
                    return;
                }

                // Shadow check
                if (!scopeStack.isEmpty()) {
                    for (Set<String> scope : scopeStack) {
                        if (scope.contains(name)) {
                            problems.add(manager.createProblemDescriptor(
                                    element,
                                    "Variable '" + name + "' shadows an outer declaration",
                                    (LocalQuickFix) null,
                                    ProblemHighlightType.WARNING,
                                    onTheFly));
                            break;
                        }
                    }
                }

                declared.put(name, element);
                if (!scopeStack.isEmpty()) scopeStack.peek().add(name);

                if (type == LarvElementTypes.CONST_DECL) {
                    constNames.add(name);
                    if (!isInsideClass(element) && !name.equals(name.toUpperCase())) {
                        problems.add(manager.createProblemDescriptor(
                                element,
                                "Constant '" + name + "' should be UPPER_SNAKE_CASE",
                                new RenameConstFix(name),
                                ProblemHighlightType.WEAK_WARNING,
                                onTheFly));
                    }
                }
            }
            checkSelfAssignment(element, name, manager, problems, onTheFly);
            checkTypeMismatch(element, manager, problems, onTheFly);
        }

        // ── assignment to const ───────────────────────────────────────────────
        if (type == LarvElementTypes.ASSIGN_STMT) {
            String lhsName = firstIdentifierText(element);
            if (lhsName != null && constNames.contains(lhsName)) {
                problems.add(manager.createProblemDescriptor(
                        element,
                        "Cannot reassign const '" + lhsName + "'",
                        (LocalQuickFix) null,
                        ProblemHighlightType.ERROR,
                        onTheFly));
            }
        }

        // ── field accessors ───────────────────────────────────────────────────
        if (type == LarvElementTypes.FIELD_ACCESSOR) {
            validateFieldAccessor(element, manager, problems, onTheFly);
        }

        // ── function declarations ─────────────────────────────────────────────
        if (type == LarvElementTypes.FUNC_DECL) {
            checkEmptyFuncBody(element, manager, problems, onTheFly);
            checkMissingReturn(element, manager, problems, onTheFly);
            checkSyncModifier(element, manager, problems, onTheFly);
        }

        // ── class declarations ────────────────────────────────────────────────
        if (type == LarvElementTypes.CLASS_DECL) {
            checkClassDecl(element, manager, problems, classHierarchy,
                    classToMethods, classCoreMethod, externalClassNames, onTheFly);
        }

        // ── override / core func declarations inside a class ─────────────────
        if (type == LarvElementTypes.FUNC_DECL && isInsideClass(element)) {
            checkOverrideAndCoreUsage(element, manager, problems,
                    classHierarchy, classOwnMethods, classCoreMethod, externalClassNames, onTheFly);
        }

        // ── variable usage tracking ───────────────────────────────────────────
        if (type == LarvElementTypes.VAR_EXPR) {
            used.add(element.getText());
        }

        if (type == LarvTokenTypes.IDENTIFIER) {
            PsiElement parent = element.getParent();
            if (parent != null) {
                IElementType pt = parent.getNode().getElementType();
                if (pt != LarvElementTypes.VAR_DECL    && pt != LarvElementTypes.CONST_DECL
                        && pt != LarvElementTypes.FUNC_DECL   && pt != LarvElementTypes.CLASS_DECL
                        && pt != LarvElementTypes.MODULE_DECL && pt != LarvElementTypes.ENUM_DECL
                        && pt != LarvElementTypes.INCLUDE_STMT) {
                    used.add(element.getText());
                }
            }
        }

        if (type == LarvElementTypes.GET_EXPR) {
            ASTNode firstChild = element.getNode().getFirstChildNode();
            if (firstChild != null) {
                IElementType ft = firstChild.getElementType();
                if (ft == LarvElementTypes.VAR_EXPR || ft == LarvTokenTypes.IDENTIFIER) {
                    String receiverName = firstChild.getText();
                    used.add(receiverName);
                    if (importedLibs.contains(receiverName)) usedLibs.add(receiverName);
                }
            }
        }

        // ── unreachable code ──────────────────────────────────────────────────
        if (type == LarvElementTypes.BLOCK) {
            checkUnreachable(element, manager, problems, onTheFly);
        }

        // ── call expressions ──────────────────────────────────────────────────
        if (type == LarvElementTypes.CALL_EXPR) {
            ASTNode firstAst = element.getNode().getFirstChildNode();
            LOG.info("[Larv Inspection] CALL_EXPR found: text='" + element.getText()
                    + "' firstChild=" + (firstAst != null ? firstAst.getElementType() : "null"));
            if (firstAst != null) {
                if (firstAst.getElementType() == LarvElementTypes.VAR_EXPR) {
                    LOG.info("[Larv Inspection] -> checkPlainCall for VAR_EXPR='" + firstAst.getText() + "'");
                    checkPlainCall(firstAst.getPsi(), element, manager, problems,
                            importedLibs, usedLibs, funcArity, onTheFly);
                } else if (firstAst.getElementType() == LarvElementTypes.GET_EXPR) {
                    LOG.info("[Larv Inspection] -> checkDotCall for GET_EXPR='" + firstAst.getText() + "'");
                    checkDotCall(firstAst.getPsi(), element, manager, problems,
                            importedLibs, usedLibs, varToClass, classToMethods, classMethodArity, onTheFly);
                } else {
                    LOG.info("[Larv Inspection] -> CALL_EXPR first child is neither VAR_EXPR nor GET_EXPR: " + firstAst.getElementType());
                }
            }
        }

        // ── illegal statements directly in class body ─────────────────────────
        if (isDirectClassChild(element)) {
            boolean allowed = type == LarvElementTypes.VAR_DECL
                    || type == LarvElementTypes.CONST_DECL
                    || type == LarvElementTypes.FUNC_DECL;
            if (!allowed && type != com.intellij.psi.TokenType.WHITE_SPACE) {
                problems.add(manager.createProblemDescriptor(
                        element,
                        "Statements are not allowed directly inside a class body",
                        (LocalQuickFix) null,
                        ProblemHighlightType.GENERIC_ERROR,
                        onTheFly));
            }
        }

        // ── recurse ───────────────────────────────────────────────────────────
        boolean opensScope = type == LarvElementTypes.BLOCK
                || type == LarvElementTypes.FUNC_DECL
                || type == LarvElementTypes.CLASS_DECL;

        if (opensScope) scopeStack.push(new LinkedHashSet<>());

        for (PsiElement child : element.getChildren()) {
            walkForInspection(child, manager, problems, declared, used, usedLibs,
                    importedLibs, varToClass, classToMethods, classOwnMethods, classHierarchy,
                    classCoreMethod, classMethodArity, funcArity, constNames, externalClassNames, scopeStack, onTheFly);
        }

        if (opensScope) scopeStack.pop();
    }

    // ── New feature checks ────────────────────────────────────────────────────

    /**
     * Validates a CLASS_DECL node for inheritance-related issues:
     * - Superclass must exist (if declared).
     * - A class may not extend itself (direct cycle).
     * - Duplicate method names within the same class body are flagged.
     */
    private void checkClassDecl(@NotNull PsiElement classDecl,
                                @NotNull InspectionManager manager,
                                @NotNull List<ProblemDescriptor> problems,
                                @NotNull Map<String, String> classHierarchy,
                                @NotNull Map<String, Set<String>> classToMethods,
                                @NotNull Map<String, Set<String>> classCoreMethod,
                                @NotNull Set<String> externalClassNames,
                                boolean onTheFly) {
        String className = firstIdentifierText(classDecl);
        if (className == null) return;

        String superName = classHierarchy.get(className);
        if (superName != null) {
            // Superclass must be declared in this file OR come from another file via include/import.
            // If it's external we cannot inspect its body, but it is a valid class — skip the error.
            boolean definedLocally   = classToMethods.containsKey(superName);
            boolean definedExternally = externalClassNames.contains(superName);
            if (!definedLocally && !definedExternally) {
                PsiElement target = findSuperclassToken(classDecl, superName);
                problems.add(manager.createProblemDescriptor(
                        target != null ? target : classDecl,
                        "Superclass '" + superName + "' is not defined",
                        (LocalQuickFix) null,
                        ProblemHighlightType.ERROR,
                        onTheFly));
            }

            // Self-extension check
            if (superName.equals(className)) {
                problems.add(manager.createProblemDescriptor(
                        classDecl,
                        "Class '" + className + "' cannot extend itself",
                        (LocalQuickFix) null,
                        ProblemHighlightType.ERROR,
                        onTheFly));
            }

            // Circular inheritance check (A extends B, B extends A)
            if (wouldCycleInheritance(className, superName, classHierarchy)) {
                problems.add(manager.createProblemDescriptor(
                        classDecl,
                        "Circular inheritance detected: '" + className + "' → '" + superName + "'",
                        (LocalQuickFix) null,
                        ProblemHighlightType.ERROR,
                        onTheFly));
            }
        }

        // Duplicate method names within the same class body
        Set<String> seen = new LinkedHashSet<>();
        for (PsiElement child : classDecl.getChildren()) {
            if (child.getNode() == null) continue;
            if (child.getNode().getElementType() == LarvElementTypes.FUNC_DECL) {
                String methodName = firstIdentifierText(child);
                if (methodName != null && !seen.add(methodName)) {
                    problems.add(manager.createProblemDescriptor(
                            child,
                            "Duplicate method '" + methodName + "' in class '" + className + "'",
                            (LocalQuickFix) null,
                            ProblemHighlightType.ERROR,
                            onTheFly));
                }
            }
        }
    }

    /**
     * Validates override and core usage on a FUNC_DECL inside a class:
     * - {@code override} without a matching parent method → error.
     * - {@code override} on a parent {@code core} method → error.
     * - Shadowing a parent method without {@code override} → warning.
     * - {@code core} on a method in a class with no subclasses is allowed (no warning needed).
     */
    private void checkOverrideAndCoreUsage(@NotNull PsiElement funcDecl,
                                           @NotNull InspectionManager manager,
                                           @NotNull List<ProblemDescriptor> problems,
                                           @NotNull Map<String, String> classHierarchy,
                                           @NotNull Map<String, Set<String>> classOwnMethods,
                                           @NotNull Map<String, Set<String>> classCoreMethod,
                                           @NotNull Set<String> externalClassNames,
                                           boolean onTheFly) {
        String methodName = firstIdentifierText(funcDecl);
        if (methodName == null) return;

        boolean isOverride = hasFuncModifier(funcDecl, LarvTokenTypes.OVERRIDE);
        boolean isCore     = hasFuncModifier(funcDecl, LarvTokenTypes.CORE);

        // Find which class this method belongs to
        String ownerClass = enclosingClassName(funcDecl);
        if (ownerClass == null) return;

        String superName = classHierarchy.get(ownerClass);

        if (isOverride) {
            if (superName == null) {
                // No superclass at all — override is meaningless
                problems.add(manager.createProblemDescriptor(
                        funcDecl,
                        "'override' on method '" + methodName + "' but class '" + ownerClass
                                + "' has no superclass",
                        new RemoveModifierFix("override"),
                        ProblemHighlightType.ERROR,
                        onTheFly));
            } else if (!externalClassNames.contains(superName)) {
                // Superclass is local — we can verify the method actually exists there
                Set<String> parentCore = classCoreMethod.getOrDefault(superName, Set.of());
                if (parentCore.contains(methodName)) {
                    problems.add(manager.createProblemDescriptor(
                            funcDecl,
                            "Cannot override 'core' method '" + methodName
                                    + "' — core methods are sealed and cannot be redefined",
                            (LocalQuickFix) null,
                            ProblemHighlightType.ERROR,
                            onTheFly));
                } else if (!parentMethodExists(methodName, superName, classHierarchy,
                        classOwnMethods)) {
                    problems.add(manager.createProblemDescriptor(
                            funcDecl,
                            "'override' on method '" + methodName
                                    + "' but no parent method with that name exists",
                            new RemoveModifierFix("override"),
                            ProblemHighlightType.ERROR,
                            onTheFly));
                }
            }
            // If superclass is external we trust the developer — no error emitted.
        } else if (!isCore && superName != null && !externalClassNames.contains(superName)) {
            // No override keyword, local superclass — check whether this silently shadows a parent method
            if (parentMethodExists(methodName, superName, classHierarchy, classOwnMethods)) {
                Set<String> parentCore = classCoreMethod.getOrDefault(superName, Set.of());
                if (parentCore.contains(methodName)) {
                    problems.add(manager.createProblemDescriptor(
                            funcDecl,
                            "Cannot shadow 'core' method '" + methodName
                                    + "' from superclass — add 'override' is not allowed either",
                            (LocalQuickFix) null,
                            ProblemHighlightType.ERROR,
                            onTheFly));
                } else {
                    problems.add(manager.createProblemDescriptor(
                            funcDecl,
                            "Method '" + methodName + "' overrides a parent method"
                                    + " but is missing the 'override' keyword",
                            new AddOverrideFix(),
                            ProblemHighlightType.WARNING,
                            onTheFly));
                }
            }
        }

        // core + override together is nonsensical
        if (isCore && isOverride) {
            problems.add(manager.createProblemDescriptor(
                    funcDecl,
                    "A method cannot be both 'core' and 'override'",
                    (LocalQuickFix) null,
                    ProblemHighlightType.ERROR,
                    onTheFly));
        }
    }

    /**
     * Checks that the {@code : sync} modifier (if present) is syntactically valid.
     * Reports if 'sync' appears without the leading colon, or if it is duplicated.
     * (Structural — the parser already enforces correctness, but we add an IDE hint.)
     */
    private void checkSyncModifier(@NotNull PsiElement funcDecl,
                                   @NotNull InspectionManager manager,
                                   @NotNull List<ProblemDescriptor> problems,
                                   boolean onTheFly) {
        boolean foundSync = hasFuncModifier(funcDecl, LarvTokenTypes.SYNC);
        if (!foundSync) return;

        // Sync on an 'init' constructor is unusual — warn
        String name = firstIdentifierText(funcDecl);
        if ("init".equals(name)) {
            problems.add(manager.createProblemDescriptor(
                    funcDecl,
                    "The 'sync' modifier on 'init' is unusual — constructors are rarely synchronized",
                    (LocalQuickFix) null,
                    ProblemHighlightType.WEAK_WARNING,
                    onTheFly));
        }
    }

    // ── Collectors ────────────────────────────────────────────────────────────

    /**
     * Collects class declarations, recording:
     * - classToMethods:  className → own method names
     * - classHierarchy:  className → superclassName (or null)
     * - classCoreMethod: className → set of own core method names
     */
    private void collectClassInfo(@NotNull PsiElement scope,
                                   @NotNull Map<String, Set<String>> classToMethods,
                                   @NotNull Map<String, Set<String>> classOwnMethods,
                                   @NotNull Map<String, String> classHierarchy,
                                   @NotNull Map<String, Set<String>> classCoreMethod,
                                   @NotNull Map<String, Map<String, Integer>> classMethodArity) {
        for (PsiElement child : scope.getChildren()) {
            if (child.getNode() == null) continue;
            if (child.getNode().getElementType() == LarvElementTypes.CLASS_DECL) {
                String className = firstIdentifierText(child);
                if (className != null) {
                    // Collect own methods
                    Set<String> methods = new LinkedHashSet<>();
                    Set<String> coreMethods = new LinkedHashSet<>();
                    Map<String, Integer> methodArity = new LinkedHashMap<>();
                    gatherFuncNamesWithModifiers(child, methods, coreMethods);
                    gatherFuncArity(child, methodArity);
                    classToMethods.put(className, methods);
                    classOwnMethods.put(className, new LinkedHashSet<>(methods)); // own-only snapshot
                    classCoreMethod.put(className, coreMethods);
                    classMethodArity.put(className, methodArity);

                    // Detect superclass from "class Foo : Bar { }" syntax
                    String superName = extractSuperclassName(child);
                    classHierarchy.put(className, superName); // null if no superclass
                }
            }
            collectClassInfo(child, classToMethods, classOwnMethods, classHierarchy, classCoreMethod, classMethodArity);
        }
    }

    /**
     * After collecting all classes, resolve inherited methods so that
     * classToMethods[Child] includes all methods visible on Child
     * (own + inherited, with child overrides winning).
     */
    private void resolveInheritedMethods(@NotNull Map<String, Set<String>> classToMethods,
                                         @NotNull Map<String, String> classHierarchy,
                                         @NotNull Map<String, Set<String>> classCoreMethod) {
        // Simple fixed-point: iterate until stable (handles chains A→B→C)
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 20) {
            changed = false;
            for (Map.Entry<String, String> entry : classHierarchy.entrySet()) {
                String child  = entry.getKey();
                String parent = entry.getValue();
                if (parent == null) continue;
                Set<String> parentMethods = classToMethods.get(parent);
                if (parentMethods == null) continue;
                Set<String> childMethods = classToMethods.computeIfAbsent(child, k -> new LinkedHashSet<>());
                for (String m : parentMethods) {
                    if (childMethods.add(m)) changed = true;
                }
            }
        }
    }

    /**
     * Resolves inherited method arities so child classes inherit parent method arities.
     */
    private void resolveInheritedMethodArity(@NotNull Map<String, Map<String, Integer>> classMethodArity,
                                             @NotNull Map<String, String> classHierarchy) {
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 20) {
            changed = false;
            for (Map.Entry<String, String> entry : classHierarchy.entrySet()) {
                String child  = entry.getKey();
                String parent = entry.getValue();
                if (parent == null) continue;
                Map<String, Integer> parentArity = classMethodArity.get(parent);
                if (parentArity == null) continue;
                Map<String, Integer> childArity = classMethodArity.computeIfAbsent(child, k -> new LinkedHashMap<>());
                for (Map.Entry<String, Integer> m : parentArity.entrySet()) {
                    if (!childArity.containsKey(m.getKey())) {
                        childArity.put(m.getKey(), m.getValue());
                        changed = true;
                    }
                }
            }
        }
    }

    /**
     * Gathers method arities (param count) for FUNC_DECL nodes inside a class scope.
     */
    private void gatherFuncArity(@NotNull PsiElement classDecl,
                                  @NotNull Map<String, Integer> methodArity) {
        for (PsiElement child : classDecl.getChildren()) {
            if (child.getNode() == null) continue;
            if (child.getNode().getElementType() == LarvElementTypes.FUNC_DECL) {
                String name = firstIdentifierText(child);
                if (name != null) {
                    int arity = 0;
                    for (PsiElement c : child.getChildren()) {
                        if (c.getNode().getElementType() == LarvElementTypes.PARAM_LIST) {
                            boolean afterName = false;
                            for (ASTNode n : c.getNode().getChildren(null)) {
                                IElementType t = n.getElementType();
                                if (t == LarvTokenTypes.IDENTIFIER) {
                                    if (!afterName) {
                                        arity++;
                                        afterName = true;
                                    }
                                } else if (t == LarvTokenTypes.COMMA) {
                                    afterName = false;
                                } else if (t == LarvTokenTypes.COLON) {
                                    afterName = true;
                                }
                            }
                        }
                    }
                    methodArity.put(name, arity);
                }
            }
        }
    }

    /**
     * Reads the superclass name from a CLASS_DECL node.
     * Syntax: {@code class Foo : Bar { }}
     * After the class name identifier, looks for COLON then IDENTIFIER.
     */
    @Nullable
    private static String extractSuperclassName(@NotNull PsiElement classDecl) {
        ASTNode[] children = classDecl.getNode().getChildren(null);
        boolean colonSeen = false;
        boolean nameSeen  = false; // skip the class's own name
        for (ASTNode n : children) {
            IElementType t = n.getElementType();
            if (t == com.intellij.psi.TokenType.WHITE_SPACE) continue;
            if (!nameSeen && t == LarvTokenTypes.IDENTIFIER) {
                nameSeen = true; // this is the class name itself
                continue;
            }
            if (nameSeen && !colonSeen && t == LarvTokenTypes.COLON) {
                colonSeen = true;
                continue;
            }
            if (colonSeen && t == LarvTokenTypes.IDENTIFIER) {
                return n.getText();
            }
            // Stop at LBRACE — no superclass found
            if (t == LarvTokenTypes.LBRACE) break;
        }
        return null;
    }

    /**
     * Gathers all method names in a class body, and separately collects
     * method names that are declared with the {@code core} modifier.
     */
    private void gatherFuncNamesWithModifiers(@NotNull PsiElement classDecl,
                                              @NotNull Set<String> allMethods,
                                              @NotNull Set<String> coreMethods) {
        for (PsiElement child : classDecl.getChildren()) {
            if (child.getNode() == null) continue;
            if (child.getNode().getElementType() == LarvElementTypes.FUNC_DECL) {
                String name = firstIdentifierText(child);
                if (name != null) {
                    allMethods.add(name);
                    if (hasFuncModifier(child, LarvTokenTypes.CORE)) {
                        coreMethods.add(name);
                    }
                }
            }
        }
    }

    /**
     * Returns true if {@code methodName} exists in {@code className} or any
     * ancestor of it in the hierarchy (stops at classes with no superclass).
     * Uses classOwnMethods which is a snapshot of each class's own (non-inherited) methods.
     */
    private boolean parentMethodExists(@NotNull String methodName,
                                       @Nullable String className,
                                       @NotNull Map<String, String> classHierarchy,
                                       @NotNull Map<String, Set<String>> classOwnMethods) {
        String current = className;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            Set<String> own = classOwnMethods.get(current);
            if (own != null && own.contains(methodName)) return true;
            current = classHierarchy.get(current);
        }
        return false;
    }

    /**
     * Checks whether a FUNC_DECL has a given modifier token (CORE, OVERRIDE, SYNC).
     * Modifier tokens appear as direct children of the FUNC_DECL node, before the
     * FUNC keyword.
     */
    private static boolean hasFuncModifier(@NotNull PsiElement funcDecl, IElementType modifier) {
        for (ASTNode n : funcDecl.getNode().getChildren(null)) {
            if (n.getElementType() == modifier) return true;
        }
        return false;
    }

    /** Returns the name of the class that directly contains this element, or null. */
    @Nullable
    private static String enclosingClassName(@NotNull PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null && !(parent instanceof PsiFile)) {
            if (parent.getNode() != null
                    && parent.getNode().getElementType() == LarvElementTypes.CLASS_DECL) {
                return firstIdentifierText(parent);
            }
            parent = parent.getParent();
        }
        return null;
    }

    /** Returns the PSI element for the superclass name token in a CLASS_DECL, or null. */
    @Nullable
    private static PsiElement findSuperclassToken(@NotNull PsiElement classDecl,
                                                  @NotNull String superName) {
        ASTNode[] children = classDecl.getNode().getChildren(null);
        boolean colonSeen = false;
        boolean nameSeen  = false;
        for (ASTNode n : children) {
            IElementType t = n.getElementType();
            if (t == com.intellij.psi.TokenType.WHITE_SPACE) continue;
            if (!nameSeen && t == LarvTokenTypes.IDENTIFIER) { nameSeen = true; continue; }
            if (nameSeen && !colonSeen && t == LarvTokenTypes.COLON) { colonSeen = true; continue; }
            if (colonSeen && t == LarvTokenTypes.IDENTIFIER && superName.equals(n.getText())) {
                return n.getPsi();
            }
        }
        return null;
    }

    /** Detects circular inheritance (A→B→A). Returns true if adding child→super would cycle. */
    private static boolean wouldCycleInheritance(@NotNull String child,
                                                 @NotNull String superName,
                                                 @NotNull Map<String, String> classHierarchy) {
        String current = superName;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(child)) return true;
            current = classHierarchy.get(current);
        }
        return false;
    }

    // ── Existing helpers (updated) ────────────────────────────────────────────

    private void checkPlainCall(@NotNull PsiElement varExpr,
                                 @NotNull PsiElement callExpr,
                                 @NotNull InspectionManager manager,
                                 @NotNull List<ProblemDescriptor> problems,
                                 @NotNull Set<String> importedLibs,
                                 @NotNull Set<String> usedLibs,
                                 @NotNull Map<String, Integer> funcArity,
                                 boolean onTheFly) {
        String calledName = varExpr.getText();
        if (calledName == null || calledName.isBlank()) return;

        int actualArity = countArguments(callExpr);
        LOG.info("[Larv Inspection] checkPlainCall: '" + calledName
                + "' actualArgs=" + actualArity
                + " funcArity=" + funcArity
                + " callExpr='" + callExpr.getText() + "'"
                + " callExprType=" + callExpr.getNode().getElementType());

        if (StdlibRegistry.BUILTINS.contains(calledName)) {
            return;
        }

        for (Map.Entry<String, List<String>> entry : STDLIB_METHODS.entrySet()) {
            if (entry.getValue().contains(calledName)) {
                String lib = entry.getKey();
                usedLibs.add(lib);
                if (!importedLibs.contains(lib)) {
                    problems.add(manager.createProblemDescriptor(
                            varExpr,
                            "'" + calledName + "' is from stdlib '" + lib
                                    + "' which is not imported. Add: import \"" + lib + "\"",
                            new InsertImportFix(lib),
                            ProblemHighlightType.ERROR,
                            onTheFly));
                }
                return;
            }
        }

        Integer expectedArity = funcArity.get(calledName);
        LOG.info("[Larv Inspection] checkPlainCall: '" + calledName
                + "' expectedArity=" + expectedArity
                + " funcArityKeys=" + funcArity.keySet());
        if (expectedArity != null) {
            if (actualArity != expectedArity) {
                LOG.info("[Larv Inspection] checkPlainCall: arity MISMATCH expected=" + expectedArity + " actual=" + actualArity);
                problems.add(manager.createProblemDescriptor(
                        callExpr,
                        "Function '" + calledName + "' expects " + expectedArity
                                + " argument(s) but got " + actualArity,
                        (LocalQuickFix) null,
                        ProblemHighlightType.ERROR,
                        onTheFly));
            }
        } else if (!funcArity.containsKey(calledName) && !importedLibs.contains(calledName)) {
            LOG.info("[Larv Inspection] checkPlainCall: function NOT FOUND in funcArity");
            // Function not found - suggest similar function names
            String suggestion = findSimilarFunction(calledName, funcArity.keySet());
            String hint = suggestion != null ? ". Did you mean '" + suggestion + "'?" : "";
            problems.add(manager.createProblemDescriptor(
                    callExpr,
                    "Function '" + calledName + "' is not defined" + hint,
                    (LocalQuickFix) null,
                    ProblemHighlightType.WARNING,
                    onTheFly));
        }
    }

    /**
     * Finds a similar function name using Levenshtein distance for suggestions.
     */
    @Nullable
    private String findSimilarFunction(@NotNull String target, @NotNull Set<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = levenshteinDistance(target.toLowerCase(), candidate.toLowerCase());
            if (distance < bestDistance && distance <= 3) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private int countArguments(@NotNull PsiElement callExpr) {
        LOG.info("[Larv Inspection] countArguments: callExpr='" + callExpr.getText() + "' children:");
        for (PsiElement child : callExpr.getChildren()) {
            IElementType t = child.getNode().getElementType();
            LOG.info("[Larv Inspection]   child: type=" + t + " text='" + child.getText() + "'");
            if (child.getNode().getElementType() == LarvElementTypes.ARG_LIST) {
                int count = 0;
                for (PsiElement arg : child.getChildren()) {
                    IElementType at = arg.getNode().getElementType();
                    LOG.info("[Larv Inspection]     arg: type=" + at + " text='" + arg.getText() + "'");
                    if (at != com.intellij.psi.TokenType.WHITE_SPACE
                            && at != LarvTokenTypes.LPAREN
                            && at != LarvTokenTypes.RPAREN
                            && at != LarvTokenTypes.COMMA) count++;
                }
                LOG.info("[Larv Inspection]   countArguments result=" + count);
                return count;
            }
        }
        LOG.info("[Larv Inspection]   countArguments: no ARG_LIST found, returning 0");
        return 0;
    }

    private void checkDotCall(@NotNull PsiElement getExpr,
                               @NotNull PsiElement callExpr,
                               @NotNull InspectionManager manager,
                               @NotNull List<ProblemDescriptor> problems,
                               @NotNull Set<String> importedLibs,
                               @NotNull Set<String> usedLibs,
                               @NotNull Map<String, String> varToClass,
                               @NotNull Map<String, Set<String>> classToMethods,
                               @NotNull Map<String, Map<String, Integer>> classMethodArity,
                               boolean onTheFly) {

        ASTNode[] nodes = getExpr.getNode().getChildren(null);
        String  receiverName = null;
        String  methodName   = null;
        ASTNode methodNode   = null;
        boolean dotSeen      = false;

        for (ASTNode n : nodes) {
            IElementType t = n.getElementType();
            if (t == com.intellij.psi.TokenType.WHITE_SPACE) continue;
            if (!dotSeen) {
                if (t == LarvElementTypes.VAR_EXPR || t == LarvTokenTypes.IDENTIFIER)
                    receiverName = n.getText();
                else if (t == LarvTokenTypes.DOT) dotSeen = true;
            } else {
                if (t == LarvTokenTypes.IDENTIFIER) { methodName = n.getText(); methodNode = n; break; }
            }
        }

        if (receiverName == null || methodName == null || methodNode == null) return;
        PsiElement methodPsi = methodNode.getPsi();

        LOG.info("[Larv Inspection] checkDotCall: receiver='" + receiverName
                + "' method='" + methodName + "' importedLibs=" + importedLibs);

        if (importedLibs.contains(receiverName)) {
            usedLibs.add(receiverName);
            List<String> allowed = STDLIB_METHODS.get(receiverName);
            if (allowed != null && !allowed.contains(methodName)) {
                String suggestion = findSimilarMethod(methodName, allowed);
                String hint = suggestion != null ? ". Did you mean '" + suggestion + "'?" : "";
                problems.add(manager.createProblemDescriptor(
                        methodPsi,
                        "Unknown method '" + methodName + "' on stdlib '" + receiverName + "'" + hint,
                        (LocalQuickFix) null,
                        ProblemHighlightType.ERROR,
                        onTheFly));
            }
            return;
        }

        String className = varToClass.get(receiverName);
        if (className != null) {
            Set<String> allowed = classToMethods.get(className);
            if (allowed != null && !allowed.contains(methodName)) {
                String suggestion = findSimilarMethod(methodName, allowed);
                String hint = suggestion != null ? ". Did you mean '" + suggestion + "'?" : "";
                problems.add(manager.createProblemDescriptor(
                        methodPsi,
                        "Unknown method '" + methodName + "' on class '" + className + "'" + hint,
                        (LocalQuickFix) null,
                        ProblemHighlightType.ERROR,
                        onTheFly));
            } else if (allowed != null && allowed.contains(methodName) && callExpr != null) {
                // Method exists — check argument count
                Map<String, Integer> arityMap = classMethodArity.get(className);
                if (arityMap != null) {
                    Integer expectedArity = arityMap.get(methodName);
                    if (expectedArity != null) {
                        int actualArity = countArguments(callExpr);
                        if (actualArity != expectedArity) {
                            problems.add(manager.createProblemDescriptor(
                                    callExpr,
                                    "Method '" + methodName + "' on class '" + className
                                            + "' expects " + expectedArity
                                            + " argument(s) but got " + actualArity,
                                    (LocalQuickFix) null,
                                    ProblemHighlightType.ERROR,
                                    onTheFly));
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds a similar method name using Levenshtein distance for suggestions.
     */
    @Nullable
    private String findSimilarMethod(@NotNull String target, @NotNull java.util.Collection<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = levenshteinDistance(target.toLowerCase(), candidate.toLowerCase());
            if (distance < bestDistance && distance <= 3) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private int levenshteinDistance(@NotNull String s1, @NotNull String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private void collectImportedLibs(@NotNull PsiElement scope,
                                     @NotNull Set<String> libs,
                                     @NotNull Map<String, PsiElement> importElements) {
        for (PsiElement child : scope.getChildren()) {
            if (child.getNode().getElementType() == LarvElementTypes.IMPORT_STMT) {
                for (ASTNode n : child.getNode().getChildren(null)) {
                    if (n.getElementType() == LarvTokenTypes.STRING) {
                        String text = n.getText();
                        if (text.length() >= 2) {
                            String lib = text.substring(1, text.length() - 1);
                            libs.add(lib);
                            importElements.put(lib, child);
                        }
                    }
                }
            }
            collectImportedLibs(child, libs, importElements);
        }
    }

    private void collectVarToClass(@NotNull PsiElement scope, @NotNull Map<String, String> map) {
        for (PsiElement child : scope.getChildren()) {
            IElementType t = child.getNode().getElementType();
            if (t == LarvElementTypes.VAR_DECL || t == LarvElementTypes.CONST_DECL) {
                String varName = firstIdentifierText(child);
                String cls     = findNewExprClass(child);
                if (varName != null && cls != null) map.put(varName, cls);
            }
            collectVarToClass(child, map);
        }
    }

    @Nullable
    private String findNewExprClass(@NotNull PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (child.getNode().getElementType() == LarvElementTypes.NEW_EXPR)
                return firstIdentifierText(child);
            String found = findNewExprClass(child);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Legacy shim — now delegates to collectClassInfo. Kept so that
     * collectFuncArity / other callers don't break.
     */
    private void gatherFuncNames(@NotNull PsiElement element, @NotNull Set<String> names) {
        for (PsiElement child : element.getChildren()) {
            if (child.getNode() == null) continue;
            if (child.getNode().getElementType() == LarvElementTypes.FUNC_DECL) {
                String name = firstIdentifierText(child);
                if (name != null) names.add(name);
            }
            gatherFuncNames(child, names);
        }
    }

    private void collectFuncArity(@NotNull PsiElement scope,
                                   @NotNull Map<String, Integer> map) {
        for (PsiElement child : scope.getChildren()) {
            if (child.getNode() == null) continue;
            if (child.getNode().getElementType() == LarvElementTypes.FUNC_DECL) {
                String name = firstIdentifierText(child);
                if (name != null) {
                    int arity = 0;
                    for (PsiElement c : child.getChildren()) {
                        if (c.getNode().getElementType() == LarvElementTypes.PARAM_LIST) {
                            boolean afterName = false;
                            for (ASTNode n : c.getNode().getChildren(null)) {
                                IElementType t = n.getElementType();
                                if (t == LarvTokenTypes.IDENTIFIER) {
                                    if (!afterName) {
                                        arity++;
                                        afterName = true;
                                    }
                                } else if (t == LarvTokenTypes.COMMA) {
                                    afterName = false;
                                } else if (t == LarvTokenTypes.COLON) {
                                    afterName = true;
                                }
                            }
                        }
                    }
                    map.put(name, arity);
                    LOG.info("[Larv Inspection] collectFuncArity: func='" + name + "' arity=" + arity);
                }
            }
            collectFuncArity(child, map);
        }
    }

    private void collectIncludes(@NotNull PsiElement scope,
                                 @NotNull Map<String, PsiElement> includeElements) {
        for (PsiElement child : scope.getChildren()) {
            if (child.getNode() == null) continue;
            if (child.getNode().getElementType() == LarvElementTypes.INCLUDE_STMT) {
                String alias = firstIdentifierText(child);
                if (alias != null) includeElements.put(alias, child);
            }
            collectIncludes(child, includeElements);
        }
    }

    /**
     * Collects names of classes (or any identifiers) that originate from other files,
     * so the superclass check can skip them instead of reporting false "not defined" errors.
     *
     * Two sources are recognised:
     *  1. {@code include Foo from "foo.larv"}  — the alias identifier ("Foo") is the class name.
     *  2. {@code import "SomeClass"}           — the bare filename stem ("SomeClass") may be a class.
     *
     * We intentionally cast a wide net: any identifier seen in these statements is treated as a
     * potentially-valid external class name. This avoids false positives without needing to
     * actually parse the referenced files.
     */
    @NotNull
    private Set<String> collectExternalClassNames(@NotNull PsiElement scope) {
        Set<String> names = new LinkedHashSet<>();
        collectExternalClassNamesRecursive(scope, names);
        return names;
    }

    private void collectExternalClassNamesRecursive(@NotNull PsiElement scope,
                                                    @NotNull Set<String> names) {
        for (PsiElement child : scope.getChildren()) {
            if (child.getNode() == null) continue;
            IElementType t = child.getNode().getElementType();

            if (t == LarvElementTypes.INCLUDE_STMT) {
                // include Foo from "bar.larv"  — first IDENTIFIER is the alias / class name
                String alias = firstIdentifierText(child);
                if (alias != null) names.add(alias);

            } else if (t == LarvElementTypes.IMPORT_STMT) {
                // import "SomeClass"  — extract the filename stem and treat it as a class name
                for (ASTNode n : child.getNode().getChildren(null)) {
                    if (n.getElementType() == LarvTokenTypes.STRING) {
                        String raw = n.getText();
                        if (raw.length() >= 2) {
                            // Strip surrounding quotes and any path/extension to get the stem
                            String path = raw.substring(1, raw.length() - 1);
                            // Take the last path segment
                            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                            String filename = (slash >= 0) ? path.substring(slash + 1) : path;
                            // Strip extension (e.g. ".larv")
                            int dot = filename.lastIndexOf('.');
                            String stem = (dot > 0) ? filename.substring(0, dot) : filename;
                            if (!stem.isEmpty()) names.add(stem);
                        }
                    }
                }
            }

            collectExternalClassNamesRecursive(child, names);
        }
    }

    /**
     * Checks that the initializer expression is compatible with the declared type annotation.
     *
     * Handles: var c : string = 3  → ERROR (int literal assigned to string)
     *          var n : int = "hi"  → ERROR (string literal assigned to int)
     *          var b : bool = 42   → ERROR (int literal assigned to bool)
     *          const X : int = 5   → OK
     *
     * Only literal initializers (numbers, strings, booleans, nil) are checked at
     * the static-analysis level; expression results are unknown without full type
     * inference, so they are silently skipped.
     */
    private void checkTypeMismatch(@NotNull PsiElement varDecl,
                                   @NotNull InspectionManager manager,
                                   @NotNull List<ProblemDescriptor> problems,
                                   boolean onTheFly) {
        // Find the TYPE_ANNOTATION child
        String declaredType = null;
        PsiElement typeAnnotNode = null;
        for (PsiElement child : varDecl.getChildren()) {
            if (child.getNode() == null) continue;
            if (child.getNode().getElementType() == LarvElementTypes.TYPE_ANNOTATION) {
                // The type keyword is a direct child of the TYPE_ANNOTATION node
                for (ASTNode n : child.getNode().getChildren(null)) {
                    IElementType t = n.getElementType();
                    if (t == LarvTokenTypes.TYPE_STRING) { declaredType = "string"; typeAnnotNode = child; break; }
                    if (t == LarvTokenTypes.TYPE_INT)    { declaredType = "int";    typeAnnotNode = child; break; }
                    if (t == LarvTokenTypes.TYPE_LONG)   { declaredType = "long";   typeAnnotNode = child; break; }
                    if (t == LarvTokenTypes.TYPE_BOOL)   { declaredType = "bool";   typeAnnotNode = child; break; }
                    if (t == LarvTokenTypes.TYPE_DOUBLE) { declaredType = "double"; typeAnnotNode = child; break; }
                    if (t == LarvTokenTypes.TYPE_FLOAT)  { declaredType = "float";  typeAnnotNode = child; break; }
                }
            }
        }
        if (declaredType == null || typeAnnotNode == null) return;

        // Find the initializer expression (last meaningful child of varDecl, skipping type annotation)
        PsiElement initExpr = null;
        for (PsiElement child : varDecl.getChildren()) {
            if (child.getNode() == null) continue;
            IElementType t = child.getNode().getElementType();
            if (t == LarvElementTypes.LITERAL_EXPR || t == LarvElementTypes.UNARY_EXPR) {
                initExpr = child;
            }
        }
        if (initExpr == null) return;

        // Detect the literal kind
        String literalKind = detectLiteralKind(initExpr);
        if (literalKind == null) return; // not a simple literal — skip

        // Check compatibility
        boolean compatible = isTypeCompatible(declaredType, literalKind);
        if (!compatible) {
            problems.add(manager.createProblemDescriptor(
                    typeAnnotNode,
                    "Type mismatch: declared type is '" + declaredType
                            + "' but the initializer is a " + literalKind + " literal",
                    (LocalQuickFix) null,
                    ProblemHighlightType.GENERIC_ERROR,
                    onTheFly));
        }
    }

    /**
     * Returns the kind of literal in a LITERAL_EXPR or UNARY_EXPR node:
     * "int", "float", "string", "bool", "nil", or null if not a simple literal.
     */
    @Nullable
    private static String detectLiteralKind(@NotNull PsiElement expr) {
        // UNARY_EXPR (e.g. -3) wraps a LITERAL_EXPR; recurse into it
        if (expr.getNode().getElementType() == LarvElementTypes.UNARY_EXPR) {
            for (PsiElement child : expr.getChildren()) {
                if (child.getNode() != null
                        && child.getNode().getElementType() == LarvElementTypes.LITERAL_EXPR) {
                    return detectLiteralKind(child);
                }
            }
            return null;
        }
        // LITERAL_EXPR: inspect the single token child
        for (ASTNode n : expr.getNode().getChildren(null)) {
            IElementType t = n.getElementType();
            if (t == LarvTokenTypes.NUMBER) {
                // Distinguish int from float by presence of '.' in text
                return n.getText().contains(".") ? "float" : "int";
            }
            if (t == LarvTokenTypes.STRING || t == LarvTokenTypes.RAW_STRING) return "string";
            if (t == LarvTokenTypes.TRUE || t == LarvTokenTypes.FALSE)        return "bool";
            if (t == LarvTokenTypes.NIL)                                       return "nil";
        }
        return null;
    }

    /**
     * Returns true if a literal of the given kind is assignable to the declared type.
     */
    private static boolean isTypeCompatible(@NotNull String declaredType, @NotNull String literalKind) {
        return switch (declaredType) {
            case "string"        -> literalKind.equals("string");
            case "int"           -> literalKind.equals("int");
            case "long"          -> literalKind.equals("int");   // int literals fit in long
            case "float"         -> literalKind.equals("int") || literalKind.equals("float");
            case "double"        -> literalKind.equals("int") || literalKind.equals("float");
            case "bool"          -> literalKind.equals("bool");
            default              -> true; // unknown type — don't complain
        };
    }

    private void checkSelfAssignment(@NotNull PsiElement varDecl,
                                     @Nullable String name,
                                     @NotNull InspectionManager manager,
                                     @NotNull List<ProblemDescriptor> problems,
                                     boolean onTheFly) {
        if (name == null) return;
        PsiElement[] children = varDecl.getChildren();
        if (children.length >= 2) {
            PsiElement rhs = children[children.length - 1];
            if (rhs.getNode().getElementType() == LarvElementTypes.VAR_EXPR
                    && name.equals(rhs.getText())) {
                problems.add(manager.createProblemDescriptor(
                        varDecl, "Variable '" + name + "' is assigned to itself",
                        (LocalQuickFix) null, ProblemHighlightType.WARNING, onTheFly));
            }
        }
    }

    private void checkEmptyFuncBody(@NotNull PsiElement funcDecl,
                                    @NotNull InspectionManager manager,
                                    @NotNull List<ProblemDescriptor> problems,
                                    boolean onTheFly) {
        for (PsiElement child : funcDecl.getChildren()) {
            if (child.getNode().getElementType() == LarvElementTypes.BLOCK
                    && child.getChildren().length == 0) {
                String name = firstIdentifierText(funcDecl);
                problems.add(manager.createProblemDescriptor(
                        funcDecl, "Function '" + (name != null ? name : "?") + "' has an empty body",
                        (LocalQuickFix) null, ProblemHighlightType.WEAK_WARNING, onTheFly));
                return;
            }
        }
    }

    private void checkMissingReturn(@NotNull PsiElement funcDecl,
                                    @NotNull InspectionManager manager,
                                    @NotNull List<ProblemDescriptor> problems,
                                    boolean onTheFly) {
        for (PsiElement child : funcDecl.getChildren()) {
            if (child.getNode().getElementType() != LarvElementTypes.BLOCK) continue;
            PsiElement[] stmts = child.getChildren();
            if (stmts.length == 0) return;

            boolean hasExprStmt = false;
            for (PsiElement stmt : stmts) {
                IElementType t = stmt.getNode().getElementType();
                if (t == LarvElementTypes.EXPR_STMT || t == LarvElementTypes.VAR_DECL) {
                    hasExprStmt = true;
                    break;
                }
            }
            if (!hasExprStmt) return;

            PsiElement last = null;
            for (int i = stmts.length - 1; i >= 0; i--) {
                IElementType t = stmts[i].getNode().getElementType();
                if (t != com.intellij.psi.TokenType.WHITE_SPACE) { last = stmts[i]; break; }
            }
            if (last == null) return;
            IElementType lastType = last.getNode().getElementType();
            if (lastType != LarvElementTypes.RETURN_STMT
                    && lastType != LarvElementTypes.THROW_STMT
                    && lastType != LarvElementTypes.IF_STMT) {
                String name = firstIdentifierText(funcDecl);
                problems.add(manager.createProblemDescriptor(
                        last,
                        "Function '" + (name != null ? name : "?") + "' may be missing a return statement",
                        (LocalQuickFix) null,
                        ProblemHighlightType.WEAK_WARNING,
                        onTheFly));
            }
        }
    }

    private static final Set<IElementType> TERMINATORS = Set.of(
            LarvElementTypes.RETURN_STMT, LarvElementTypes.THROW_STMT);

    private void checkUnreachable(@NotNull PsiElement block,
                                  @NotNull InspectionManager manager,
                                  @NotNull List<ProblemDescriptor> problems,
                                  boolean onTheFly) {
        boolean terminated = false;
        for (PsiElement stmt : block.getChildren()) {
            IElementType t = stmt.getNode().getElementType();
            if (terminated && t != com.intellij.psi.TokenType.WHITE_SPACE) {
                problems.add(manager.createProblemDescriptor(
                        stmt, "Unreachable code",
                        (LocalQuickFix) null, ProblemHighlightType.WARNING, onTheFly));
                break;
            }
            if (TERMINATORS.contains(t)) terminated = true;
            if (t == LarvTokenTypes.BREAK || t == LarvTokenTypes.CONTINUE) terminated = true;
        }
    }

    private void validateFieldAccessor(@NotNull PsiElement accessor,
                                       @NotNull InspectionManager manager,
                                       @NotNull List<ProblemDescriptor> problems,
                                       boolean onTheFly) {
        boolean hasGet = false;
        boolean hasSet = false;

        for (ASTNode child : accessor.getNode().getChildren(null)) {
            IElementType t = child.getElementType();
            if (t == com.intellij.psi.TokenType.WHITE_SPACE
                    || t == LarvTokenTypes.COLON
                    || t == LarvTokenTypes.COMMA) continue;
            // Skip error-recovery / incomplete nodes (e.g. while user is still typing)
            // createProblemDescriptor crashes on empty PsiElements
            PsiElement childPsi = child.getPsi();
            if (childPsi == null || childPsi.getTextLength() == 0) continue;
            if (t == com.intellij.psi.TokenType.ERROR_ELEMENT) continue;

            if (t == LarvTokenTypes.GET) {
                if (hasGet) problems.add(manager.createProblemDescriptor(childPsi,
                        "Duplicate accessor 'get'", (LocalQuickFix) null,
                        ProblemHighlightType.WARNING, onTheFly));
                hasGet = true;
            } else if (t == LarvTokenTypes.SET) {
                if (hasSet) problems.add(manager.createProblemDescriptor(childPsi,
                        "Duplicate accessor 'set'", (LocalQuickFix) null,
                        ProblemHighlightType.WARNING, onTheFly));
                hasSet = true;
            } else {
                problems.add(manager.createProblemDescriptor(childPsi,
                        "Unknown accessor '" + child.getText() + "': only 'get' and 'set' are allowed",
                        (LocalQuickFix) null, ProblemHighlightType.ERROR, onTheFly));
            }
        }

        if (hasSet) {
            PsiElement fieldDecl = accessor.getParent();
            if (fieldDecl != null
                    && fieldDecl.getNode().getElementType() == LarvElementTypes.CONST_DECL) {
                problems.add(manager.createProblemDescriptor(accessor,
                        "Cannot declare 'set' accessor on a 'const' field — const fields are read-only",
                        new RemoveSetAccessorFix(), ProblemHighlightType.ERROR, onTheFly));
            }
        }

        if (!isInsideClass(accessor)) {
            problems.add(manager.createProblemDescriptor(accessor,
                    "Property accessors ('get'/'set') are only meaningful on class fields",
                    (LocalQuickFix) null, ProblemHighlightType.WARNING, onTheFly));
        }
    }

    private static boolean isInsideClass(@NotNull PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null && !(parent instanceof PsiFile)) {
            if (parent.getNode().getElementType() == LarvElementTypes.CLASS_DECL) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private static boolean isDirectClassChild(@NotNull PsiElement element) {
        PsiElement parent = element.getParent();
        if (parent == null || parent.getNode() == null) return false;
        if (parent.getNode().getElementType() != LarvElementTypes.BLOCK) return false;
        PsiElement grandParent = parent.getParent();
        if (grandParent == null || grandParent.getNode() == null) return false;
        return grandParent.getNode().getElementType() == LarvElementTypes.CLASS_DECL;
    }

    @Nullable
    private static String firstIdentifierText(@NotNull PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (child.getNode().getElementType() == LarvTokenTypes.IDENTIFIER)
                return child.getText();
        }
        for (ASTNode n : element.getNode().getChildren(null)) {
            if (n.getElementType() == LarvTokenTypes.IDENTIFIER) return n.getText();
        }
        return null;
    }

    // ── Quick Fixes ───────────────────────────────────────────────────────────

    private static final class RemoveIncludeFix implements LocalQuickFix {
        @Override public @NotNull String getName()       { return "Remove unused include"; }
        @Override public @NotNull String getFamilyName() { return "Larv fixes"; }
        @Override public void applyFix(@NotNull Project p, @NotNull ProblemDescriptor d) {
            d.getPsiElement().delete();
        }
    }

    private static final class RemoveImportFix implements LocalQuickFix {
        @Override public @NotNull String getName()       { return "Remove unused import"; }
        @Override public @NotNull String getFamilyName() { return "Larv fixes"; }
        @Override public void applyFix(@NotNull Project p, @NotNull ProblemDescriptor d) {
            d.getPsiElement().delete();
        }
    }

    private static final class RenameConstFix implements LocalQuickFix {
        private final String original;
        RenameConstFix(String n) { this.original = n; }
        @Override public @NotNull String getName()       { return "Rename to '" + toUpperSnake(original) + "'"; }
        @Override public @NotNull String getFamilyName() { return "Larv fixes"; }
        @Override public void applyFix(@NotNull Project p, @NotNull ProblemDescriptor d) {
            replaceAll(d.getPsiElement().getContainingFile(), original, toUpperSnake(original));
        }
        private static void replaceAll(@NotNull PsiElement root, String from, String to) {
            if (root.getNode().getElementType() == LarvTokenTypes.IDENTIFIER
                    && root.getText().equals(from)) {
                com.intellij.openapi.editor.Editor ed =
                        com.intellij.psi.util.PsiEditorUtil.findEditor(root);
                if (ed != null) ed.getDocument().replaceString(
                        root.getTextRange().getStartOffset(),
                        root.getTextRange().getEndOffset(), to);
            }
            for (PsiElement child : root.getChildren()) replaceAll(child, from, to);
        }
        private static String toUpperSnake(String name) {
            return name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
        }
    }

    private static final class InsertImportFix implements LocalQuickFix {
        private final String libName;
        InsertImportFix(String lib) { this.libName = lib; }
        @Override public @NotNull String getName()       { return "Add import \"" + libName + "\""; }
        @Override public @NotNull String getFamilyName() { return "Larv fixes"; }
        @Override public void applyFix(@NotNull Project p, @NotNull ProblemDescriptor d) {
            com.intellij.openapi.editor.Editor ed =
                    com.intellij.psi.util.PsiEditorUtil.findEditor(d.getPsiElement());
            if (ed != null) ed.getDocument().insertString(0, "import \"" + libName + "\"\n");
        }
    }

    private static final class RemoveSetAccessorFix implements LocalQuickFix {
        @Override public @NotNull String getName()       { return "Remove 'set' accessor from const field"; }
        @Override public @NotNull String getFamilyName() { return "Larv fixes"; }
        @Override public void applyFix(@NotNull Project p, @NotNull ProblemDescriptor d) {
            d.getPsiElement().delete();
        }
    }

    /** Removes a modifier keyword token (core / override / sync) from a FUNC_DECL. */
    private static final class RemoveModifierFix implements LocalQuickFix {
        private final String modifierName;
        RemoveModifierFix(String name) { this.modifierName = name; }
        @Override public @NotNull String getName()       { return "Remove '" + modifierName + "' modifier"; }
        @Override public @NotNull String getFamilyName() { return "Larv fixes"; }
        @Override public void applyFix(@NotNull Project p, @NotNull ProblemDescriptor d) {
            PsiElement funcDecl = d.getPsiElement();
            for (ASTNode n : funcDecl.getNode().getChildren(null)) {
                if (n.getText().equals(modifierName)) { n.getPsi().delete(); return; }
            }
        }
    }

    /** Inserts the {@code override} keyword before {@code func} in a FUNC_DECL. */
    private static final class AddOverrideFix implements LocalQuickFix {
        @Override public @NotNull String getName()       { return "Add 'override' modifier"; }
        @Override public @NotNull String getFamilyName() { return "Larv fixes"; }
        @Override public void applyFix(@NotNull Project p, @NotNull ProblemDescriptor d) {
            PsiElement funcDecl = d.getPsiElement();
            com.intellij.openapi.editor.Editor ed =
                    com.intellij.psi.util.PsiEditorUtil.findEditor(funcDecl);
            if (ed != null) {
                int offset = funcDecl.getTextRange().getStartOffset();
                ed.getDocument().insertString(offset, "override ");
            }
        }
    }

    // ── Stdlib registry ───────────────────────────────────────────────────────

    private static final Map<String, List<String>> STDLIB_METHODS = new LinkedHashMap<>();

    static {
        STDLIB_METHODS.put("math", List.of(
                "sqrt", "pow", "abs", "floor", "ceil", "round", "max", "min",
                "log", "log10", "sin", "cos", "tan", "asin", "acos", "atan",
                "atan2", "toRadians", "toDegrees", "random", "randomInt",
                "clamp", "sign", "pi", "e", "isNaN", "isInfinite", "toInt"
        ));
        STDLIB_METHODS.put("io", List.of(
                "readFile", "writeFile", "appendFile", "readLines", "readBytes",
                "writeBytes", "deleteFile", "fileExists", "isDir", "listDir",
                "makeDir", "copyFile", "moveFile", "fileSize", "cwd", "absPath"
        ));
        STDLIB_METHODS.put("string", List.of(
                "strLen", "strUpper", "strLower", "strTrim", "strTrimLeft", "strTrimRight",
                "strContains", "strStartsWith", "strEndsWith", "strIndexOf", "strSlice",
                "strReplace", "strReplaceAll", "strSplit", "strJoin", "strRepeat",
                "strReverse", "strCharAt", "strToNumber", "strFromNumber", "strIsEmpty",
                "strPadLeft", "strPadRight", "strChars"
        ));
        STDLIB_METHODS.put("list", List.of(
                "listNew", "listAdd", "listAddAt", "listRemove", "listGet", "listSet",
                "listSize", "listContains", "listIndexOf", "listSlice", "listReverse",
                "listSort", "listConcat", "listFlat", "listUnique", "listFill",
                "listClear", "listIsEmpty", "listFirst", "listLast", "listPop", "listShuffle"
        ));
        STDLIB_METHODS.put("map", List.of(
                "mapNew", "mapSet", "mapGet", "mapHas", "mapRemove", "mapSize",
                "mapKeys", "mapValues", "mapClear", "mapIsEmpty", "mapMerge",
                "mapContainsValue", "mapToList"
        ));
        STDLIB_METHODS.put("http", List.of(
                "httpGet", "httpPost", "httpPostJson", "httpPut", "httpDelete", "httpRequest"
        ));
        STDLIB_METHODS.put("system", List.of(
                "exit", "getArgs", "getEnv", "clock", "nanoTime", "sleep",
                "exec", "osName", "osArch", "freeMemory", "totalMemory", "gc"
        ));
        STDLIB_METHODS.put("date", List.of(
                "timestamp", "dateNow", "timeNow", "dateTimeNow", "dateFormat", "dateParse",
                "dateAdd", "dateSub", "dateDiff", "dayOfWeek", "monthName",
                "year", "month", "day", "hour", "minute", "second", "isBefore", "isAfter"
        ));
        STDLIB_METHODS.put("base64", List.of(
                "base64Encode", "base64Decode", "base64EncodeUrl", "base64DecodeUrl",
                "hashMd5", "hashSha1", "hashSha256", "hashSha512",
                "hexEncode", "hexDecode", "urlEncode", "urlDecode"
        ));
        STDLIB_METHODS.put("regex", List.of(
                "regexMatch", "regexTest", "regexFind", "regexFindAll",
                "regexReplace", "regexReplaceAll", "regexSplit", "regexGroup", "regexGroups"
        ));
        STDLIB_METHODS.put("converter", List.of(
                "toNumber", "toString", "toBool", "toInt", "toHex", "toOctal", "toBinary",
                "fromHex", "fromOctal", "fromBinary", "toBytes", "fromBytes", "typeOf"
        ));
        STDLIB_METHODS.put("properties", List.of(
                "loadProp", "getProp", "setProp", "saveProp", "getAllProp"
        ));
    }
}