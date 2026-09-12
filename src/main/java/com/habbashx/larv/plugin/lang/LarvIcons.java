package com.habbashx.larv.plugin.lang;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public final class LarvIcons {

    public static final Icon FILE     = IconLoader.getIcon("/icons/larv_file.svg", LarvIcons.class);
    public static final Icon LARV_CLASS = IconLoader.getIcon("/icons/larv-class.svg", LarvIcons.class);
    public static final Icon LARV_ENUM = IconLoader.getIcon("/icons/larv-enum.svg", LarvIcons.class);
    public static final Icon LARV_MODULE = IconLoader.getIcon("/icons/larv-module.svg", LarvIcons.class);
    public static final Icon FUNCTION = AllIcons.Nodes.Method;
    public static final Icon CLASS    = AllIcons.Nodes.Class;
    public static final Icon VARIABLE = AllIcons.Nodes.Variable;
    public static final Icon CONSTANT = AllIcons.Nodes.Constant;
}
