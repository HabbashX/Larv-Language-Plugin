package com.habbashx.larv.plugin.parser;

import com.habbashx.larv.plugin.lang.LarvLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;

public final class LarvElementTypes {

    private LarvElementTypes() {}

    public static final IFileElementType FILE = new IFileElementType(LarvLanguage.INSTANCE);


    public static final IElementType VAR_DECL      = e("VAR_DECL");
    public static final IElementType CONST_DECL    = e("CONST_DECL");
    public static final IElementType FUNC_DECL     = e("FUNC_DECL");
    public static final IElementType CLASS_DECL    = e("CLASS_DECL");
    public static final IElementType MODULE_DECL   = e("MODULE_DECL");
    public static final IElementType ENUM_DECL     = e("ENUM_DECL");
    public static final IElementType INTERFACE_DECL = e("INTERFACE_DECL");
    public static final IElementType IMPORT_STMT   = e("IMPORT_STMT");
    public static final IElementType INCLUDE_STMT  = e("INCLUDE_STMT");
    public static final IElementType IF_STMT       = e("IF_STMT");
    public static final IElementType WHILE_STMT    = e("WHILE_STMT");
    public static final IElementType FOR_STMT      = e("FOR_STMT");
    public static final IElementType FOREACH_STMT  = e("FOREACH_STMT");
    public static final IElementType RETURN_STMT   = e("RETURN_STMT");
    public static final IElementType PRINT_STMT    = e("PRINT_STMT");
    public static final IElementType TRY_STMT      = e("TRY_STMT");
    public static final IElementType THROW_STMT    = e("THROW_STMT");
    public static final IElementType SWITCH_STMT   = e("SWITCH_STMT");
    public static final IElementType BLOCK          = e("BLOCK");
    public static final IElementType EXPR_STMT     = e("EXPR_STMT");
    public static final IElementType ASSIGN_STMT   = e("ASSIGN_STMT");

    public static final IElementType BINARY_EXPR   = e("BINARY_EXPR");
    public static final IElementType UNARY_EXPR    = e("UNARY_EXPR");
    public static final IElementType CALL_EXPR     = e("CALL_EXPR");
    public static final IElementType GET_EXPR      = e("GET_EXPR");
    public static final IElementType INDEX_EXPR    = e("INDEX_EXPR");
    public static final IElementType ARRAY_EXPR    = e("ARRAY_EXPR");
    public static final IElementType NEW_EXPR      = e("NEW_EXPR");
    public static final IElementType AWAIT_EXPR    = e("AWAIT_EXPR");
    public static final IElementType NON_NULL_EXPR = e("NON_NULL_EXPR");
    public static final IElementType TERNARY_EXPR  = e("TERNARY_EXPR");
    public static final IElementType LITERAL_EXPR  = e("LITERAL_EXPR");
    public static final IElementType VAR_EXPR      = e("VAR_EXPR");
    public static final IElementType PARAM_LIST    = e("PARAM_LIST");
    public static final IElementType ARG_LIST      = e("ARG_LIST");

    /**
     * Represents the accessor modifier list after a field declaration, e.g.:
     *   var a = 3 : set,get
     *   const c = 3 : get
     *
     * AST children: COLON  (GET | SET)*  (COMMA (GET | SET))*
     */
    public static final IElementType FIELD_ACCESSOR = e("FIELD_ACCESSOR");

    /**
     * Represents the type annotation on a variable or const declaration, e.g.:
     *   var c : string = 3
     *   const MAX : int = 100
     *
     * AST children: COLON  TYPE_* (one of TYPE_INT, TYPE_STRING, etc.)
     */
    public static final IElementType TYPE_ANNOTATION = e("TYPE_ANNOTATION");

    private static IElementType e(String name) {
        return new IElementType(name, LarvLanguage.getInstance());
    }
}