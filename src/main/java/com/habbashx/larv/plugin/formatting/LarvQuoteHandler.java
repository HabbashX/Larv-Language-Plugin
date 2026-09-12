package com.habbashx.larv.plugin.formatting;

import com.habbashx.larv.plugin.lexer.LarvTokenTypes;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;

public final class LarvQuoteHandler extends SimpleTokenSetQuoteHandler {
    public LarvQuoteHandler() {
        super(LarvTokenTypes.STRING_LITERALS);
    }
}