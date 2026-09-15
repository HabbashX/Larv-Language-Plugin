package com.habbashx.larv.plugin.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.habbashx.larv.plugin.lexer.LarvTokenTypes.*;

public class LarvLexer extends LexerBase {

    private CharSequence buffer;
    private int          bufferStart;
    private int          bufferEnd;
    private int          tokenStart;
    private int          tokenEnd;
    private IElementType tokenType;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer      = buffer;
        this.bufferStart = Math.max(0, startOffset);
        this.bufferEnd   = Math.min(buffer.length(), endOffset);
        this.tokenStart  = this.bufferStart;
        this.tokenEnd    = this.bufferStart;
        advance();
    }

    @Override public int                    getState()           { return 0; }
    @Override public @Nullable IElementType getTokenType()       { return tokenType; }
    @Override public int                    getTokenStart()      { return tokenStart; }
    @Override public int                    getTokenEnd()        { return tokenEnd; }
    @Override public @NotNull CharSequence  getBufferSequence()  { return buffer; }
    @Override public int                    getBufferEnd()       { return bufferEnd; }

    @Override
    public void advance() {
        tokenStart = tokenEnd;
        if (tokenStart >= bufferEnd) {
            tokenType = null;
            return;
        }
        tokenType = nextToken();
        if (tokenEnd > bufferEnd) tokenEnd = bufferEnd;
    }

    private IElementType nextToken() {
        int  pos = tokenStart;
        char c   = buffer.charAt(pos);

        // Whitespace
        if (isWhitespace(c)) {
            while (pos < bufferEnd && isWhitespace(buffer.charAt(pos))) pos++;
            tokenEnd = pos;
            return com.intellij.psi.TokenType.WHITE_SPACE;
        }

        // Line comment  //
        if (c == '/' && pos + 1 < bufferEnd && buffer.charAt(pos + 1) == '/') {
            pos += 2;
            while (pos < bufferEnd && buffer.charAt(pos) != '\n') pos++;
            tokenEnd = pos;
            return LarvTokenTypes.COMMENT;
        }

        // String / raw string
        if (c == '"') {
            // Triple-quote raw string  """..."""
            if (pos + 2 < bufferEnd && buffer.charAt(pos + 1) == '"' && buffer.charAt(pos + 2) == '"') {
                pos += 3;
                while (pos + 2 < bufferEnd) {
                    if (buffer.charAt(pos) == '"' && buffer.charAt(pos+1) == '"' && buffer.charAt(pos+2) == '"') {
                        pos += 3;
                        break;
                    }
                    pos++;
                }
                if (pos + 2 >= bufferEnd) pos = bufferEnd;
                tokenEnd = pos;
                return LarvTokenTypes.RAW_STRING;
            }
            // Normal string  "..."
            pos++;
            while (pos < bufferEnd) {
                char ch = buffer.charAt(pos);
                if (ch == '\\' && pos + 1 < bufferEnd) { pos += 2; continue; }
                if (ch == '"')  { pos++; break; }
                if (ch == '\n') break;
                pos++;
            }
            tokenEnd = pos;
            return LarvTokenTypes.STRING;
        }

        if (isDigit(c)) {
            while (pos < bufferEnd && isDigit(buffer.charAt(pos))) pos++;
            if (pos < bufferEnd && buffer.charAt(pos) == '.'
                    && pos + 1 < bufferEnd && isDigit(buffer.charAt(pos + 1))) {
                pos++;
                while (pos < bufferEnd && isDigit(buffer.charAt(pos))) pos++;
            }
            tokenEnd = pos;
            return LarvTokenTypes.NUMBER;
        }

        // Identifier / keyword
        if (isAlpha(c)) {
            while (pos < bufferEnd && isAlphaNum(buffer.charAt(pos))) pos++;
            tokenEnd = pos;
            return keyword(buffer.subSequence(tokenStart, tokenEnd).toString());
        }

        // Operators and punctuation
        pos++;
        char next = (pos < bufferEnd) ? buffer.charAt(pos) : '\0';

        switch (c) {
            case '(':  tokenEnd = pos; return LarvTokenTypes.LPAREN;
            case ')':  tokenEnd = pos; return LarvTokenTypes.RPAREN;
            case '{':  tokenEnd = pos; return LarvTokenTypes.LBRACE;
            case '}':  tokenEnd = pos; return LarvTokenTypes.RBRACE;
            case '[':  tokenEnd = pos; return LarvTokenTypes.LBRACKET;
            case ']':  tokenEnd = pos; return LarvTokenTypes.RBRACKET;
            case ',':  tokenEnd = pos; return LarvTokenTypes.COMMA;
            case ';':  tokenEnd = pos; return LarvTokenTypes.SEMICOLON;
            case ':':  tokenEnd = pos; return LarvTokenTypes.COLON;
            case '.':  tokenEnd = pos; return LarvTokenTypes.DOT;
            case '?':  tokenEnd = pos; return LarvTokenTypes.QUESTION;
            case '+':
                if (next == '+') { tokenEnd = pos + 1; return LarvTokenTypes.PLUS_PLUS; }
                if (next == '=') { tokenEnd = pos + 1; return LarvTokenTypes.PLUS_EQUAL; }
                tokenEnd = pos; return LarvTokenTypes.PLUS;
            case '-':
                // -> arrow for return-type annotation
                if (next == '>') { tokenEnd = pos + 1; return LarvTokenTypes.ARROW; }
                if (next == '-') { tokenEnd = pos + 1; return LarvTokenTypes.MINUS_MINUS; }
                if (next == '=') { tokenEnd = pos + 1; return LarvTokenTypes.MINUS_EQUAL; }
                tokenEnd = pos; return LarvTokenTypes.MINUS;
            case '*':
                if (next == '=') { tokenEnd = pos + 1; return LarvTokenTypes.STAR_EQUAL; }
                tokenEnd = pos; return LarvTokenTypes.STAR;
            case '/':
                if (next == '=') { tokenEnd = pos + 1; return LarvTokenTypes.SLASH_EQUAL; }
                tokenEnd = pos; return LarvTokenTypes.SLASH;
            case '=':
                if (next == '=') { tokenEnd = pos + 1; return LarvTokenTypes.EQEQ; }
                tokenEnd = pos; return LarvTokenTypes.EQUAL;
            case '!':
                if (next == '=') { tokenEnd = pos + 1; return LarvTokenTypes.NOTEQ; }
                tokenEnd = pos; return LarvTokenTypes.BANG;
            case '<':
                if (next == '=') { tokenEnd = pos + 1; return LarvTokenTypes.LTE; }
                tokenEnd = pos; return LarvTokenTypes.LT;
            case '>':
                if (next == '=') { tokenEnd = pos + 1; return LarvTokenTypes.GTE; }
                tokenEnd = pos; return LarvTokenTypes.GT;
            case '&':
                if (next == '&') { tokenEnd = pos + 1; return LarvTokenTypes.AND; }
                tokenEnd = pos; return LarvTokenTypes.BAD_CHAR;
            case '|':
                if (next == '|') { tokenEnd = pos + 1; return LarvTokenTypes.OR; }
                tokenEnd = pos; return LarvTokenTypes.BAD_CHAR;
            default:
                tokenEnd = pos;
                return LarvTokenTypes.BAD_CHAR;
        }
    }

    @Contract(pure = true)
    private static IElementType keyword(@NotNull String w) {
        return switch (w) {
            case "var"      -> LarvTokenTypes.VAR;
            case "const"    -> LarvTokenTypes.CONST;
            case "if"       -> LarvTokenTypes.IF;
            case "else"     -> LarvTokenTypes.ELSE;
            case "while"    -> LarvTokenTypes.WHILE;
            case "for"      -> LarvTokenTypes.FOR;
            case "in"       -> LarvTokenTypes.IN;
            case "func"     -> LarvTokenTypes.FUNC;
            case "return"   -> LarvTokenTypes.RETURN;
            case "break"    -> LarvTokenTypes.BREAK;
            case "continue" -> LarvTokenTypes.CONTINUE;
            case "class"    -> LarvTokenTypes.CLASS;
            case "new"      -> LarvTokenTypes.NEW;
            case "this"     -> LarvTokenTypes.THIS;
            case "include"  -> LarvTokenTypes.INCLUDE;
            case "from"     -> LarvTokenTypes.FROM;
            case "involve"  -> LarvTokenTypes.INVOLVE;
            case "import"   -> LarvTokenTypes.IMPORT;
            case "module"   -> LarvTokenTypes.MODULE;
            case "as"       -> LarvTokenTypes.AS;
            case "nil"      -> LarvTokenTypes.NIL;
            case "true"     -> LarvTokenTypes.TRUE;
            case "false"    -> LarvTokenTypes.FALSE;
            case "try"      -> LarvTokenTypes.TRY;
            case "catch"    -> LarvTokenTypes.CATCH;
            case "finally"  -> LarvTokenTypes.FINALLY;
            case "throw"    -> LarvTokenTypes.THROW;
            case "switch"   -> LarvTokenTypes.SWITCH;
            case "case"     -> LarvTokenTypes.CASE;
            case "default"  -> LarvTokenTypes.DEFAULT;
            case "enum"     -> LarvTokenTypes.ENUM;
            case "get"      -> LarvTokenTypes.GET;
            case "set"      -> LarvTokenTypes.SET;
            case "core"     -> LarvTokenTypes.CORE;
            case "sync"     -> LarvTokenTypes.SYNC;
            case "override" -> LarvTokenTypes.OVERRIDE;
            case "defer"    -> LarvTokenTypes.DEFER;
            case "atomic"   -> LarvTokenTypes.ATOMIC;
            case "volatile" -> LarvTokenTypes.VOLATILE;
            case "async"    -> LarvTokenTypes.ASYNC;
            case "await"    -> LarvTokenTypes.AWAIT;
            case "interface" -> LarvTokenTypes.INTERFACE;
            case "implements" -> LarvTokenTypes.IMPLEMENTS;
            case "is"       -> LarvTokenTypes.IS;
            case "string"   -> LarvTokenTypes.TYPE_STRING;
            case "int"      -> LarvTokenTypes.TYPE_INT;
            case "bool"     -> LarvTokenTypes.TYPE_BOOL;
            case "float"    -> LarvTokenTypes.TYPE_FLOAT;
            case "double"   -> LarvTokenTypes.TYPE_DOUBLE;
            case "long"     -> LarvTokenTypes.TYPE_LONG;
            case "char"     -> LarvTokenTypes.TYPE_CHAR;
            case "byte"     -> LarvTokenTypes.TYPE_BYTE;
            case "short"    -> LarvTokenTypes.TYPE_SHORT;
            case "bigint"   -> LarvTokenTypes.TYPE_BIGINT;
            case "smallint" -> LarvTokenTypes.TYPE_SMALLINT;
            case "any"      -> LarvTokenTypes.TYPE_ANY;
            case "object"   -> LarvTokenTypes.TYPE_OBJECT;
            default         -> LarvTokenTypes.IDENTIFIER;
        };
    }

    private static boolean isWhitespace(char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }
    private static boolean isDigit(char c)       { return c >= '0' && c <= '9'; }
    private static boolean isAlpha(char c)       { return Character.isLetter(c) || c == '_'; }
    private static boolean isAlphaNum(char c)    { return isAlpha(c) || isDigit(c); }
}