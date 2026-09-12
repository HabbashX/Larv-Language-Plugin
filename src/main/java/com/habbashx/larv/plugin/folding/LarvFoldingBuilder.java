package com.habbashx.larv.plugin.folding;

import com.habbashx.larv.plugin.lexer.LarvTokenTypes;
import com.habbashx.larv.plugin.parser.LarvElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class LarvFoldingBuilder extends FoldingBuilderEx {

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(
            @NotNull PsiElement root, @NotNull Document document, boolean quick) {

        List<FoldingDescriptor> descriptors = new ArrayList<>();
        collectFoldable(root, document, descriptors);
        return descriptors.toArray(FoldingDescriptor.EMPTY);
    }

    private void collectFoldable(@NotNull PsiElement element,
                                 @NotNull Document document,
                                 @NotNull List<FoldingDescriptor> out) {
        IElementType type = element.getNode().getElementType();

        if (type == LarvElementTypes.BLOCK
                || type == LarvElementTypes.FUNC_DECL
                || type == LarvElementTypes.CLASS_DECL
                || type == LarvElementTypes.MODULE_DECL
                || type == LarvElementTypes.ENUM_DECL
                || type == LarvElementTypes.TRY_STMT
                || type == LarvElementTypes.IF_STMT
                || type == LarvElementTypes.WHILE_STMT
                || type == LarvElementTypes.FOR_STMT
                || type == LarvElementTypes.FOREACH_STMT
                || type == LarvElementTypes.SWITCH_STMT) {
            TextRange range = element.getTextRange();
            if (range.getLength() > 2) {
                int startLine = document.getLineNumber(range.getStartOffset());
                int endLine   = document.getLineNumber(range.getEndOffset() - 1);
                if (endLine > startLine) {
                    out.add(new FoldingDescriptor(element.getNode(), range));
                }
            }
        }

        if (type == LarvTokenTypes.RAW_STRING) {
            TextRange range = element.getTextRange();
            if (range.getLength() > 6) {
                out.add(new FoldingDescriptor(element.getNode(), range));
            }
        }

        for (PsiElement child : element.getChildren()) {
            collectFoldable(child, document, out);
        }
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        IElementType type = node.getElementType();
        if (type == LarvTokenTypes.RAW_STRING) return "\"\"\"...\"\"\"";
        if (type == LarvElementTypes.FUNC_DECL) {
            // Show: func name(...) {...}
            return "func " + firstIdChild(node) + "(...) { ... }";
        }
        if (type == LarvElementTypes.CLASS_DECL)  return "class "  + firstIdChild(node) + " { ... }";
        if (type == LarvElementTypes.MODULE_DECL) return "module " + firstIdChild(node) + " { ... }";
        if (type == LarvElementTypes.ENUM_DECL)   return "enum "   + firstIdChild(node) + " { ... }";
        return "{ ... }";
    }

    @Override public boolean isCollapsedByDefault(@NotNull ASTNode node) { return false; }

    private static String firstIdChild(@NotNull ASTNode node) {
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == LarvTokenTypes.IDENTIFIER) return child.getText();
            child = child.getTreeNext();
        }
        return "?";
    }
}