package com.habbashx.larv.plugin.lang;

import com.intellij.lang.Language;

public final class LarvLanguage extends Language {

    public static final LarvLanguage INSTANCE = new LarvLanguage();

    private LarvLanguage() {
        super("Larv");
    }

    public static LarvLanguage getInstance(){
        return INSTANCE;
    }


}