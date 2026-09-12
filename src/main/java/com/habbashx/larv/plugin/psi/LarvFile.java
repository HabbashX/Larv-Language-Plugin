package com.habbashx.larv.plugin.psi;

import com.habbashx.larv.plugin.lang.LarvFileType;
import com.habbashx.larv.plugin.lang.LarvLanguage;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class LarvFile extends PsiFileBase {

    public LarvFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, LarvLanguage.INSTANCE);
    }

    @Override public @NotNull FileType getFileType() { return LarvFileType.INSTANCE; }
    @Override public String toString() { return "Larv File"; }
}