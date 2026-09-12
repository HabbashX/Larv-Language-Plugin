package com.habbashx.larv.plugin.highlighting;

import com.habbashx.larv.plugin.lexer.LarvLexerAdapter;
import com.habbashx.larv.plugin.lexer.LarvTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public final class LarvSyntaxHighlighter extends SyntaxHighlighterBase {

    public static final TextAttributesKey KEYWORD = createTextAttributesKey(
            "LARV_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);

    public static final TextAttributesKey STRING_KEY = createTextAttributesKey(
            "LARV_STRING", DefaultLanguageHighlighterColors.STRING);

    public static final TextAttributesKey NUMBER_KEY = createTextAttributesKey(
            "LARV_NUMBER", DefaultLanguageHighlighterColors.NUMBER);

    public static final TextAttributesKey COMMENT_KEY = createTextAttributesKey(
            "LARV_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

    public static final TextAttributesKey OPERATOR_KEY = createTextAttributesKey(
            "LARV_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);

    public static final TextAttributesKey IDENTIFIER_KEY = createTextAttributesKey(
            "LARV_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);

    public static final TextAttributesKey BRACES_KEY = createTextAttributesKey(
            "LARV_BRACES", DefaultLanguageHighlighterColors.BRACES);

    public static final TextAttributesKey BRACKETS_KEY = createTextAttributesKey(
            "LARV_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);

    public static final TextAttributesKey PARENS_KEY = createTextAttributesKey(
            "LARV_PARENS", DefaultLanguageHighlighterColors.PARENTHESES);

    public static final TextAttributesKey COMMA_KEY = createTextAttributesKey(
            "LARV_COMMA", DefaultLanguageHighlighterColors.COMMA);

    public static final TextAttributesKey DOT_KEY = createTextAttributesKey(
            "LARV_DOT", DefaultLanguageHighlighterColors.DOT);

    public static final TextAttributesKey SEMICOLON_KEY = createTextAttributesKey(
            "LARV_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);

    public static final TextAttributesKey BAD_CHAR_KEY = createTextAttributesKey(
            "LARV_BAD_CHAR", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE);

    public static final TextAttributesKey CLASS_CONST_KEY = createTextAttributesKey(
            "LARV_CLASS_CONST", DefaultLanguageHighlighterColors.CONSTANT);

    public static final TextAttributesKey PARAM_KEY = createTextAttributesKey(
            "LARV_PARAM", DefaultLanguageHighlighterColors.PARAMETER);

    /**
     * Cyan-tinted key used for:
     *  - type names in parameter annotations:  func foo(s: string)  → "string" is cyan
     *  - return-type annotations:              func foo() -> string  → "string" is cyan
     *  - the type argument inside atomic<…>:   atomic<int>          → "int" is cyan
     */
    public static final TextAttributesKey TYPE_REF_KEY = createTextAttributesKey(
            "LARV_TYPE_REF", DefaultLanguageHighlighterColors.METADATA);

    private static final TextAttributesKey[] KEYWORDS_KEYS  = keys(KEYWORD);
    private static final TextAttributesKey[] STRING_KEYS     = keys(STRING_KEY);
    private static final TextAttributesKey[] NUMBER_KEYS     = keys(NUMBER_KEY);
    private static final TextAttributesKey[] COMMENT_KEYS    = keys(COMMENT_KEY);
    private static final TextAttributesKey[] OPERATOR_KEYS   = keys(OPERATOR_KEY);
    private static final TextAttributesKey[] IDENTIFIER_KEYS = keys(IDENTIFIER_KEY);
    private static final TextAttributesKey[] BRACE_KEYS      = keys(BRACES_KEY);
    private static final TextAttributesKey[] BRACKET_KEYS    = keys(BRACKETS_KEY);
    private static final TextAttributesKey[] PAREN_KEYS      = keys(PARENS_KEY);
    private static final TextAttributesKey[] COMMA_KEYS      = keys(COMMA_KEY);
    private static final TextAttributesKey[] DOT_KEYS        = keys(DOT_KEY);
    private static final TextAttributesKey[] SEMI_KEYS       = keys(SEMICOLON_KEY);
    private static final TextAttributesKey[] BAD_KEYS        = keys(BAD_CHAR_KEY);
    private static final TextAttributesKey[] EMPTY           = TextAttributesKey.EMPTY_ARRAY;
    private static final TextAttributesKey[] TYPE_REF_KEYS   = keys(TYPE_REF_KEY);

    @Override public @NotNull Lexer getHighlightingLexer() { return new LarvLexerAdapter(); }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType type) {
        if (LarvTokenTypes.KEYWORDS.contains(type))                 return KEYWORDS_KEYS;
        if (LarvTokenTypes.LITERALS.contains(type)
                && type != LarvTokenTypes.NUMBER
                && type != LarvTokenTypes.STRING
                && type != LarvTokenTypes.RAW_STRING)               return KEYWORDS_KEYS;
        if (LarvTokenTypes.OPERATORS.contains(type))                return OPERATOR_KEYS;
        if (type == LarvTokenTypes.ARROW)                           return OPERATOR_KEYS;
        if (type == LarvTokenTypes.STRING
                || type == LarvTokenTypes.RAW_STRING)               return STRING_KEYS;
        if (type == LarvTokenTypes.NUMBER)                          return NUMBER_KEYS;
        if (type == LarvTokenTypes.COMMENT)                         return COMMENT_KEYS;
        if (type == LarvTokenTypes.IDENTIFIER)                      return IDENTIFIER_KEYS;
        if (LarvTokenTypes.BRACES.contains(type))                   return BRACE_KEYS;
        if (LarvTokenTypes.BRACKETS.contains(type))                 return BRACKET_KEYS;
        if (LarvTokenTypes.PARENS.contains(type))                   return PAREN_KEYS;
        if (type == LarvTokenTypes.COMMA)                           return COMMA_KEYS;
        if (type == LarvTokenTypes.DOT)                             return DOT_KEYS;
        if (type == LarvTokenTypes.SEMICOLON)                       return SEMI_KEYS;
        if (type == LarvTokenTypes.BAD_CHAR)                        return BAD_KEYS;
        if (LarvTokenTypes.BUILTIN_TYPES.contains(type))            return TYPE_REF_KEYS;
        return EMPTY;
    }

    private static TextAttributesKey[] keys(TextAttributesKey... k) { return k; }
}