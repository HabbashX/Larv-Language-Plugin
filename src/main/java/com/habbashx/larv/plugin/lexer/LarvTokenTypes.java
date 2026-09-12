package com.habbashx.larv.plugin.lexer;

import com.habbashx.larv.plugin.lang.LarvLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class LarvTokenTypes {

    public static final IElementType PERCENT  = t("PERCENT");
    public static final IElementType CORE     = t("CORE");
    public static final IElementType SYNC     = t("SYNC");
    public static final IElementType OVERRIDE = t("OVERRIDE");
    public static final IElementType DEFER    = t("DEFER");
    public static final IElementType ATOMIC   = t("ATOMIC");
    public static final IElementType VOLATILE = t("VOLATILE");
    public static final IElementType ASYNC    = t("ASYNC");
    public static final IElementType AWAIT    = t("AWAIT");
    public static final IElementType INTERFACE = t("INTERFACE");
    public static final IElementType IMPLEMENTS = t("IMPLEMENTS");
    public static final IElementType IS       = t("IS");

    public static final IElementType TYPE_INT    = t("TYPE_INT");
    public static final IElementType TYPE_LONG   = t("TYPE_LONG");
    public static final IElementType TYPE_BOOL   = t("TYPE_BOOL");
    public static final IElementType TYPE_DOUBLE = t("TYPE_DOUBLE");
    public static final IElementType TYPE_STRING = t("TYPE_STRING");
    public static final IElementType TYPE_FLOAT  = t("TYPE_FLOAT");


    private LarvTokenTypes() {}

    public static final IElementType NUMBER     = t("NUMBER");
    public static final IElementType STRING     = t("STRING");
    public static final IElementType RAW_STRING = t("RAW_STRING");
    public static final IElementType IDENTIFIER = t("IDENTIFIER");

    public static final IElementType VAR      = t("VAR");
    public static final IElementType CONST    = t("CONST");
    public static final IElementType PRINT    = t("PRINT");    // built-in print statement
    public static final IElementType IF       = t("IF");
    public static final IElementType ELSE     = t("ELSE");
    public static final IElementType WHILE    = t("WHILE");
    public static final IElementType FOR      = t("FOR");
    public static final IElementType IN       = t("IN");
    public static final IElementType FUNC     = t("FUNC");
    public static final IElementType RETURN   = t("RETURN");
    public static final IElementType BREAK    = t("BREAK");
    public static final IElementType CONTINUE = t("CONTINUE");
    public static final IElementType CLASS    = t("CLASS");
    public static final IElementType NEW      = t("NEW");
    public static final IElementType THIS     = t("THIS");
    public static final IElementType INCLUDE  = t("INCLUDE");
    public static final IElementType FROM     = t("FROM");
    public static final IElementType INVOLVE  = t("INVOLVE");
    public static final IElementType IMPORT   = t("IMPORT");
    public static final IElementType MODULE   = t("MODULE");
    public static final IElementType AS       = t("AS");
    public static final IElementType NIL      = t("NIL");
    public static final IElementType TRUE     = t("TRUE");
    public static final IElementType FALSE    = t("FALSE");
    public static final IElementType TRY      = t("TRY");
    public static final IElementType CATCH    = t("CATCH");
    public static final IElementType FINALLY  = t("FINALLY");
    public static final IElementType THROW    = t("THROW");
    public static final IElementType SWITCH   = t("SWITCH");
    public static final IElementType CASE     = t("CASE");
    public static final IElementType DEFAULT  = t("DEFAULT");
    public static final IElementType ENUM     = t("ENUM");
    public static final IElementType GET      = t("GET");
    public static final IElementType SET      = t("SET");

    // ── Operators ─────────────────────────────────────────────────────────────
    public static final IElementType PLUS        = t("PLUS");
    public static final IElementType MINUS       = t("MINUS");
    public static final IElementType STAR        = t("STAR");
    public static final IElementType SLASH       = t("SLASH");
    public static final IElementType PLUS_PLUS   = t("PLUS_PLUS");
    public static final IElementType MINUS_MINUS = t("MINUS_MINUS");
    public static final IElementType EQUAL       = t("EQUAL");
    public static final IElementType PLUS_EQUAL  = t("PLUS_EQUAL");
    public static final IElementType MINUS_EQUAL = t("MINUS_EQUAL");
    public static final IElementType STAR_EQUAL  = t("STAR_EQUAL");
    public static final IElementType SLASH_EQUAL = t("SLASH_EQUAL");
    public static final IElementType EQEQ        = t("EQEQ");
    public static final IElementType NOTEQ       = t("NOTEQ");
    public static final IElementType LT          = t("LT");
    public static final IElementType GT          = t("GT");
    public static final IElementType LTE         = t("LTE");
    public static final IElementType GTE         = t("GTE");
    public static final IElementType AND         = t("AND");
    public static final IElementType OR          = t("OR");
    public static final IElementType BANG        = t("BANG");
    public static final IElementType QUESTION    = t("QUESTION");
    /** The -> arrow used in function return-type annotations: func foo() -> string {} */
    public static final IElementType ARROW       = t("ARROW");

    // ── Punctuation ───────────────────────────────────────────────────────────
    public static final IElementType LPAREN    = t("LPAREN");
    public static final IElementType RPAREN    = t("RPAREN");
    public static final IElementType LBRACE    = t("LBRACE");
    public static final IElementType RBRACE    = t("RBRACE");
    public static final IElementType LBRACKET  = t("LBRACKET");
    public static final IElementType RBRACKET  = t("RBRACKET");
    public static final IElementType COMMA     = t("COMMA");
    public static final IElementType SEMICOLON = t("SEMICOLON");
    public static final IElementType COLON     = t("COLON");
    public static final IElementType DOT       = t("DOT");

    public static final IElementType COMMENT  = t("COMMENT");
    public static final IElementType BAD_CHAR = t("BAD_CHAR");

    public static final TokenSet KEYWORDS = TokenSet.create(
            VAR, CONST, IF, ELSE, WHILE, FOR, IN, FUNC, RETURN,
            BREAK, CONTINUE, CLASS, NEW, THIS, INCLUDE, FROM, INVOLVE,
            IMPORT, MODULE, AS, TRY, CATCH, FINALLY, THROW,
            SWITCH, CASE, DEFAULT, ENUM, GET, SET,
            OVERRIDE, CORE, SYNC, DEFER, ATOMIC, VOLATILE, ASYNC, AWAIT,
            INTERFACE, IMPLEMENTS, IS
    );

    public static final TokenSet LITERALS = TokenSet.create(
            NUMBER, STRING, RAW_STRING, NIL, TRUE, FALSE
    );

    public static final TokenSet OPERATORS = TokenSet.create(
            PLUS, MINUS, STAR, SLASH, PLUS_PLUS, MINUS_MINUS,
            EQUAL, PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL,
            EQEQ, NOTEQ, LT, GT, LTE, GTE, AND, OR, BANG, QUESTION
    );

    public static final TokenSet BRACES    = TokenSet.create(LBRACE, RBRACE);
    public static final TokenSet PARENS    = TokenSet.create(LPAREN, RPAREN);
    public static final TokenSet BRACKETS  = TokenSet.create(LBRACKET, RBRACKET);
    public static final TokenSet STRING_LITERALS = TokenSet.create(STRING, RAW_STRING);
    public static final TokenSet COMMENTS_SET    = TokenSet.create(COMMENT);
    public static final TokenSet BUILTIN_TYPES   = TokenSet.create(
            TYPE_INT, TYPE_LONG, TYPE_BOOL, TYPE_DOUBLE, TYPE_STRING, TYPE_FLOAT);

    @Contract("_ -> new")
    private static @NotNull IElementType t(String name) {
        return new IElementType(name, LarvLanguage.getInstance());
    }
}
