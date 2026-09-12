package com.habbashx.larv.plugin.annotator;

import com.habbashx.larv.plugin.highlighting.LarvSyntaxHighlighter;
import com.habbashx.larv.plugin.lexer.LarvTokenTypes;
import com.habbashx.larv.plugin.parser.LarvElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class LarvAnnotator implements Annotator {

    public static final TextAttributesKey FUNC_DECL_KEY =
            TextAttributesKey.createTextAttributesKey(
                    "LARV_FUNC_DECL", com.intellij.openapi.editor.DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);

    private static final TextAttributesKey FUNC_CALL_KEY =
            TextAttributesKey.createTextAttributesKey(
                    "LARV_FUNC_CALL", com.intellij.openapi.editor.DefaultLanguageHighlighterColors.FUNCTION_CALL);

    private static final TextAttributesKey CLASS_NAME_KEY =
            TextAttributesKey.createTextAttributesKey(
                    "LARV_CLASS_NAME", com.intellij.openapi.editor.DefaultLanguageHighlighterColors.CLASS_NAME);

    private static final TextAttributesKey CONST_NAME_KEY =
            TextAttributesKey.createTextAttributesKey(
                    "LARV_CONST_NAME", com.intellij.openapi.editor.DefaultLanguageHighlighterColors.CONSTANT);

    public static final TextAttributesKey PARAM_KEY =
            TextAttributesKey.createTextAttributesKey(
                    "LARV_PARAM", com.intellij.openapi.editor.DefaultLanguageHighlighterColors.PARAMETER);

    public static final TextAttributesKey REFERENCE_KEY =
            TextAttributesKey.createTextAttributesKey(
                    "LARV_REFERENCE",
                    com.intellij.openapi.editor.DefaultLanguageHighlighterColors.CONSTANT);

    private static final Set<String> BUILTINS =
            Set.of("input", "len", "range", "print", "printErr");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() == null) return;
        IElementType type = element.getNode().getElementType();

        // ── Bad character error ──────────────────────────────────────────────
        if (type == LarvTokenTypes.BAD_CHAR) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "Unexpected character — did you mean '&&' or '||'?")
                    .range(element)
                    .create();
            return;
        }

        // ── get/set inside field-accessor stays keyword-coloured ─────────────
        if ((type == LarvTokenTypes.GET || type == LarvTokenTypes.SET)
                && element.getParent() != null
                && element.getParent().getNode() != null
                && element.getParent().getNode().getElementType() == LarvElementTypes.FIELD_ACCESSOR) {
            setHighlight(element, holder, LarvSyntaxHighlighter.KEYWORD);
            return;
        }

        // ── Return-type annotation:  func foo() -> <TYPE>  ──────────────────
        // The IDENTIFIER token that follows an ARROW token is a return type → cyan.
        if (type == LarvTokenTypes.IDENTIFIER) {
            ASTNode prev = skipWhitespaceBackward(element.getNode().getTreePrev());
            if (prev != null && prev.getElementType() == LarvTokenTypes.ARROW) {
                setHighlight(element, holder, LarvSyntaxHighlighter.TYPE_REF_KEY);
                return;
            }
        }

        // ── Parameter type annotation:  func foo(s: <TYPE>)  ────────────────
        // An IDENTIFIER that follows a COLON inside a PARAM_LIST → cyan.
        if (type == LarvTokenTypes.IDENTIFIER
                && element.getParent() != null
                && element.getParent().getNode() != null
                && element.getParent().getNode().getElementType() == LarvElementTypes.PARAM_LIST) {
            ASTNode prev = skipWhitespaceBackward(element.getNode().getTreePrev());
            if (prev != null && prev.getElementType() == LarvTokenTypes.COLON) {
                setHighlight(element, holder, LarvSyntaxHighlighter.TYPE_REF_KEY);
                return;
            }
        }

        // ── atomic<TYPE> — the type argument inside angle brackets → cyan ────
        // Pattern in source:  atomic < IDENTIFIER >
        // We look at the raw text window around the element: if the character
        // sequence immediately before the identifier (in the file) is "atomic<"
        // (with optional whitespace between < and the identifier), colour it cyan.
        // Using the document text is the most reliable approach since the PSI tree
        // wrapping can vary by IntelliJ version.
        if (type == LarvTokenTypes.IDENTIFIER) {
            PsiElement atomicCandidate = element.getParent();
            if (atomicCandidate != null) {
                // Walk backwards through PSI siblings to find: LT then ATOMIC
                PsiElement sib = element.getPrevSibling();
                // skip whitespace PSI nodes
                while (sib != null && sib.getNode() != null
                        && sib.getNode().getElementType() == com.intellij.psi.TokenType.WHITE_SPACE) {
                    sib = sib.getPrevSibling();
                }
                if (sib != null && sib.getNode() != null
                        && sib.getNode().getElementType() == LarvTokenTypes.LT) {
                    PsiElement sib2 = sib.getPrevSibling();
                    while (sib2 != null && sib2.getNode() != null
                            && sib2.getNode().getElementType() == com.intellij.psi.TokenType.WHITE_SPACE) {
                        sib2 = sib2.getPrevSibling();
                    }
                    if (sib2 != null && sib2.getNode() != null
                            && sib2.getNode().getElementType() == LarvTokenTypes.ATOMIC) {
                        setHighlight(element, holder, LarvSyntaxHighlighter.TYPE_REF_KEY);
                        return;
                    }
                }
            }
        }

        // From here on we only care about IDENTIFIER tokens
        if (type != LarvTokenTypes.IDENTIFIER) return;

        String text = element.getText();
        PsiElement parent = element.getParent();
        if (parent == null || parent.getNode() == null) return;

        IElementType parentType = parent.getNode().getElementType();

        if (parentType == LarvElementTypes.INCLUDE_STMT) {
            setHighlight(element, holder, REFERENCE_KEY);
            return;
        }

        com.intellij.psi.PsiFile file = element.getContainingFile();
        if (file != null && isIncludeAlias(text, file)) {
            setHighlight(element, holder, REFERENCE_KEY);
            return;
        }

        if (parentType == LarvElementTypes.PARAM_LIST) {
            setHighlight(element, holder, PARAM_KEY);
            return;
        }

        if (parentType == LarvElementTypes.FUNC_DECL) {
            setHighlight(element, holder, FUNC_DECL_KEY);
            return;
        }

        if (parentType == LarvElementTypes.CLASS_DECL
                || parentType == LarvElementTypes.MODULE_DECL
                || parentType == LarvElementTypes.ENUM_DECL) {
            setHighlight(element, holder, CLASS_NAME_KEY);
            return;
        }

        if (parentType == LarvElementTypes.NEW_EXPR) {
            setHighlight(element, holder, CLASS_NAME_KEY);
            return;
        }

        if (parentType == LarvElementTypes.CALL_EXPR
                || parentType == LarvElementTypes.VAR_EXPR) {
            PsiElement grandparent = parent.getParent();
            if (grandparent != null
                    && grandparent.getNode().getElementType() == LarvElementTypes.CALL_EXPR) {
                setHighlight(element, holder, FUNC_CALL_KEY);
                return;
            }
        }

        if (BUILTINS.contains(text)) {
            setHighlight(element, holder, LarvSyntaxHighlighter.KEYWORD);
            return;
        }

        if (text.equals(text.toUpperCase()) && text.length() > 1
                && text.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
            setHighlight(element, holder, CONST_NAME_KEY);
        }
    }

    /** Returns true if the given name is declared as an include alias in the file. */
    private static boolean isIncludeAlias(@NotNull String name, @NotNull com.intellij.psi.PsiFile file) {
        for (com.intellij.psi.PsiElement child : file.getChildren()) {
            if (child.getNode() == null) continue;
            if (child.getNode().getElementType() == LarvElementTypes.INCLUDE_STMT) {
                String alias = firstIdentifierText(child);
                if (name.equals(alias)) return true;
            }
        }
        return false;
    }

    @org.jetbrains.annotations.Nullable
    private static String firstIdentifierText(@NotNull com.intellij.psi.PsiElement element) {
        for (ASTNode n : element.getNode().getChildren(null)) {
            if (n.getElementType() == LarvTokenTypes.IDENTIFIER) return n.getText();
        }
        return null;
    }


    @Contract("null -> null")
    private static ASTNode skipWhitespaceBackward(ASTNode node) {
        while (node != null && node.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE) {
            node = node.getTreePrev();
        }
        return node;
    }

    private static void setHighlight(@NotNull PsiElement element,
                                     @NotNull AnnotationHolder holder,
                                     @NotNull TextAttributesKey key) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(key)
                .create();
    }
}
