package com.habbashx.larv.plugin.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class LarvPsiElement extends ASTWrapperPsiElement {
    public LarvPsiElement(@NotNull ASTNode node) {
        super(node);
    }
}