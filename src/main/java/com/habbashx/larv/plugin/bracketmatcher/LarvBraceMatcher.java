package com.habbashx.larv.plugin.bracketmatcher;

import com.habbashx.larv.plugin.lexer.LarvTokenTypes;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LarvBraceMatcher implements PairedBraceMatcher {

    private static final BracePair[] PAIRS = {
            new BracePair(LarvTokenTypes.LBRACE,   LarvTokenTypes.RBRACE,   true),
            new BracePair(LarvTokenTypes.LPAREN,   LarvTokenTypes.RPAREN,   false),
            new BracePair(LarvTokenTypes.LBRACKET, LarvTokenTypes.RBRACKET, false),
    };

    @Override public BracePair @NotNull [] getPairs() { return PAIRS; }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType,
                                                   @Nullable IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}