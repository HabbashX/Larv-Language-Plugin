package com.habbashx.larv.plugin.commenter;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public final class LarvCommenter implements CodeDocumentationAwareCommenter {

    @Override public @Nullable String getLineCommentPrefix()       { return "// "; }
    @Override public @Nullable String getBlockCommentPrefix()      { return null; }
    @Override public @Nullable String getBlockCommentSuffix()      { return null; }
    @Override public @Nullable String getCommentedBlockCommentPrefix() { return null; }
    @Override public @Nullable String getCommentedBlockCommentSuffix() { return null; }
    @Override public @Nullable IElementType getLineCommentTokenType()  { return com.habbashx.larv.plugin.lexer.LarvTokenTypes.COMMENT; }
    @Override public @Nullable IElementType getBlockCommentTokenType() { return null; }
    @Override public @Nullable IElementType getDocumentationCommentTokenType() { return null; }
    @Override public @Nullable String getDocumentationCommentPrefix()  { return null; }
    @Override public @Nullable String getDocumentationCommentLinePrefix() { return null; }
    @Override public @Nullable String getDocumentationCommentSuffix()  { return null; }
    @Override public boolean isDocumentationComment(PsiComment element) { return false; }
}