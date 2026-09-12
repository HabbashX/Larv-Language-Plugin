package com.habbashx.larv.plugin.structure;

import com.habbashx.larv.plugin.lang.LarvIcons;
import com.habbashx.larv.plugin.lexer.LarvTokenTypes;
import com.habbashx.larv.plugin.parser.LarvElementTypes;
import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class LarvStructureViewFactory implements PsiStructureViewFactory {

    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        return new TreeBasedStructureViewBuilder() {
            @Override
            public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new LarvStructureViewModel(psiFile, editor);
            }
        };
    }

    static final class LarvStructureViewModel
            extends StructureViewModelBase
            implements StructureViewModel.ElementInfoProvider {

        LarvStructureViewModel(@NotNull PsiFile file, @Nullable Editor editor) {
            super(file, editor, new LarvFileTreeElement(file));
        }

        @Override public boolean isAlwaysShowsPlus(StructureViewTreeElement e) { return false; }

        @Override public boolean isAlwaysLeaf(StructureViewTreeElement e) {
            if (e instanceof LarvDeclTreeElement decl) {
                IElementType t = decl.type;
                return t == LarvElementTypes.VAR_DECL
                        || t == LarvElementTypes.CONST_DECL
                        || t == LarvElementTypes.FUNC_DECL;
            }
            return false;
        }
    }


    static final class LarvFileTreeElement implements StructureViewTreeElement {
        private final PsiFile file;
        LarvFileTreeElement(PsiFile file) { this.file = file; }

        @Override public Object getValue() { return file; }

        @Override public void navigate(boolean requestFocus) {
            if (file instanceof com.intellij.pom.Navigatable n) n.navigate(requestFocus);
        }
        @Override public boolean canNavigate()         { return file instanceof com.intellij.pom.Navigatable; }
        @Override public boolean canNavigateToSource() { return canNavigate(); }

        @Override
        public @NotNull ItemPresentation getPresentation() {
            return new ItemPresentation() {
                @Override public @Nullable String getPresentableText() { return file.getName(); }
                @Override public @Nullable Icon getIcon(boolean unused) { return LarvIcons.FILE; }
            };
        }

        @Override
        public TreeElement @NotNull [] getChildren() {
            List<TreeElement> kids = new ArrayList<>();
            collectTopLevel(file, kids);
            return kids.toArray(TreeElement.EMPTY_ARRAY);
        }

        private static void collectTopLevel(PsiElement root, List<TreeElement> out) {
            for (PsiElement child : root.getChildren()) {
                IElementType type = child.getNode().getElementType();
                if (type == LarvElementTypes.FUNC_DECL
                        || type == LarvElementTypes.CLASS_DECL
                        || type == LarvElementTypes.MODULE_DECL
                        || type == LarvElementTypes.ENUM_DECL
                        || type == LarvElementTypes.VAR_DECL
                        || type == LarvElementTypes.CONST_DECL) {
                    out.add(new LarvDeclTreeElement(child, type));
                } else {
                    collectTopLevel(child, out);
                }
            }
        }
    }


    static final class LarvDeclTreeElement implements StructureViewTreeElement {
        final PsiElement   element;
        final IElementType type;

        LarvDeclTreeElement(PsiElement element, IElementType type) {
            this.element = element;
            this.type    = type;
        }

        @Override public Object getValue() { return element; }

        @Override public void navigate(boolean req) {
            if (element instanceof com.intellij.pom.Navigatable n) n.navigate(req);
        }
        @Override public boolean canNavigate()         { return element instanceof com.intellij.pom.Navigatable; }
        @Override public boolean canNavigateToSource() { return canNavigate(); }

        @Override
        public @NotNull ItemPresentation getPresentation() {
            String name = firstIdentifier(element);
            Icon   icon = iconFor(type);
            String label = (type == LarvElementTypes.FUNC_DECL) ? name + "()" : name;
            return new ItemPresentation() {
                @Override public @Nullable String getPresentableText() { return label; }
                @Override public @Nullable Icon   getIcon(boolean unused) { return icon; }
            };
        }

        @Override
        public TreeElement @NotNull [] getChildren() {
            if (type == LarvElementTypes.CLASS_DECL
                    || type == LarvElementTypes.MODULE_DECL
                    || type == LarvElementTypes.ENUM_DECL) {
                List<TreeElement> kids = new ArrayList<>();
                for (PsiElement child : element.getChildren()) {
                    IElementType ct = child.getNode().getElementType();
                    if (ct == LarvElementTypes.BLOCK) {
                        for (PsiElement inner : child.getChildren()) {
                            IElementType it = inner.getNode().getElementType();
                            if (it == LarvElementTypes.FUNC_DECL
                                    || it == LarvElementTypes.VAR_DECL
                                    || it == LarvElementTypes.CONST_DECL
                                    || it == LarvElementTypes.CLASS_DECL
                                    || it == LarvElementTypes.MODULE_DECL
                                    || it == LarvElementTypes.ENUM_DECL) {
                                kids.add(new LarvDeclTreeElement(inner, it));
                            }
                        }
                    }
                }
                return kids.toArray(TreeElement.EMPTY_ARRAY);
            }
            return TreeElement.EMPTY_ARRAY;
        }

        /**
         * Find the first IDENTIFIER token by walking AST children directly.
         * Using getChildren() (PSI children) skips token-level nodes, so we
         * walk getNode().getFirstChildNode() instead.
         */
        private static String firstIdentifier(PsiElement element) {
            ASTNode node = element.getNode().getFirstChildNode();
            while (node != null) {
                if (node.getElementType() == LarvTokenTypes.IDENTIFIER) {
                    return node.getText();
                }
                node = node.getTreeNext();
            }
            return "?";
        }

        private static Icon iconFor(IElementType type) {
            if (type == LarvElementTypes.FUNC_DECL)   return LarvIcons.FUNCTION;
            if (type == LarvElementTypes.CLASS_DECL)  return LarvIcons.CLASS;
            if (type == LarvElementTypes.MODULE_DECL) return LarvIcons.CLASS;
            if (type == LarvElementTypes.ENUM_DECL)   return LarvIcons.CLASS;
            if (type == LarvElementTypes.CONST_DECL)  return LarvIcons.CONSTANT;
            return LarvIcons.VARIABLE;
        }
    }
}
