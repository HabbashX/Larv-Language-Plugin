package com.habbashx.larv.plugin.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class LarvFileType extends LanguageFileType {

    public static final LarvFileType INSTANCE = new LarvFileType();
    public static final String EXTENSION      = "larv";

    private LarvFileType() {
        super(LarvLanguage.INSTANCE);
    }

    @Override public @NotNull String getName()        { return "Larv File"; }
    @Override public @NotNull String getDescription() { return "Larv language source file"; }
    @Override public @NotNull String getDefaultExtension() { return EXTENSION; }
    @Override public @Nullable Icon getIcon()         { return LarvIcons.FILE; }
}