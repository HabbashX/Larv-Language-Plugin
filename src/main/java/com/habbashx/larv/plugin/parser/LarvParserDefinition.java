package com.habbashx.larv.plugin.parser;

import com.habbashx.larv.plugin.lexer.LarvLexerAdapter;
import com.habbashx.larv.plugin.lexer.LarvTokenTypes;
import com.habbashx.larv.plugin.psi.LarvFile;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public final class LarvParserDefinition implements ParserDefinition {

    @Override public @NotNull Lexer createLexer(Project project) { return new LarvLexerAdapter(); }

    @Override public @NotNull PsiParser createParser(Project project) { return new LarvPsiParser(); }

    @Override public @NotNull IFileElementType getFileNodeType() { return LarvElementTypes.FILE; }

    @Override public @NotNull TokenSet getCommentTokens()        { return LarvTokenTypes.COMMENTS_SET; }

    @Override public @NotNull TokenSet getStringLiteralElements(){ return LarvTokenTypes.STRING_LITERALS; }

    @Override public @NotNull PsiElement createElement(ASTNode node) {
        return new com.habbashx.larv.plugin.psi.LarvPsiElement(node);
    }

    @Override public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new LarvFile(viewProvider);
    }
}