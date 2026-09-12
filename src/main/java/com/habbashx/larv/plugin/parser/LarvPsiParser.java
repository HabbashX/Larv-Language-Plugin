package com.habbashx.larv.plugin.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import static com.habbashx.larv.plugin.lexer.LarvTokenTypes.*;
import static com.habbashx.larv.plugin.parser.LarvElementTypes.*;

public final class LarvPsiParser implements PsiParser, LightPsiParser {

    @Override
    public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        parseLight(root, builder);
        return builder.getTreeBuilt();
    }

    @Override
    public void parseLight(IElementType root, PsiBuilder builder) {
        builder.setDebugMode(false);
        PsiBuilder.Marker file = builder.mark();
        while (!builder.eof()) {
            parseStatement(builder);
        }
        file.done(root);
    }

    private void parseStatement(@NotNull PsiBuilder b) {
        IElementType tt = b.getTokenType();

        if (tt == VAR)      { parseVarDecl(b);    return; }
        if (tt == CONST)    { parseConstDecl(b);  return; }
        if (tt == FUNC)     { parseFuncDecl(b);   return; }
        if (tt == OVERRIDE || tt == CORE || tt == SYNC || tt == DEFER || tt == ASYNC) { parseFuncDecl(b); return; }
        if (tt == ATOMIC)   { parseAtomicDecl(b); return; }
        if (tt == VOLATILE) { parseVolatileDecl(b); return; }
        if (tt == CLASS)    { parseClassDecl(b);  return; }
        if (tt == INTERFACE) { parseInterfaceDecl(b); return; }
        if (tt == MODULE)   { parseModuleDecl(b); return; }
        if (tt == ENUM)     { parseEnumDecl(b);   return; }
        if (tt == IMPORT)   { parseImport(b);     return; }
        if (tt == INCLUDE)  { parseInclude(b);    return; }
        if (tt == IF)       { parseIf(b);         return; }
        if (tt == WHILE)    { parseWhile(b);      return; }
        if (tt == FOR)      { parseFor(b);        return; }
        if (tt == RETURN)   { parseReturn(b);     return; }
        if (tt == PRINT)    { parsePrint(b);      return; }
        if (tt == TRY)      { parseTryCatch(b);   return; }
        if (tt == THROW)    { parseThrow(b);      return; }
        if (tt == SWITCH)   { parseSwitch(b);     return; }
        if (tt == BREAK || tt == CONTINUE) { b.advanceLexer(); return; }
        if (tt == LBRACE)   { parseBlock(b);      return; }

        parseExprStatement(b);
    }

    private void parseVarDecl(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, VAR);
        expect(b, IDENTIFIER);
        // Optional type annotation:  var name : TypeName = expr
        // Distinguishable from field accessors because TYPE_* tokens are NOT GET/SET.
        if (b.getTokenType() == COLON) {
            IElementType next = b.lookAhead(1);
            if (next != null && BUILTIN_TYPES.contains(next)) {
                // Parse type annotation node
                PsiBuilder.Marker ta = b.mark();
                b.advanceLexer(); // consume ':'
                b.advanceLexer(); // consume type keyword
                ta.done(TYPE_ANNOTATION);
            }
        }
        if (b.getTokenType() == EQUAL) {
            b.advanceLexer();
            parseExpression(b);
        }
        if (b.getTokenType() == COLON) {
            parseFieldAccessor(b);
        }
        m.done(VAR_DECL);
    }

    private void parseConstDecl(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, CONST);
        expect(b, IDENTIFIER);
        // Optional type annotation:  const NAME : TypeName = expr
        if (b.getTokenType() == COLON) {
            IElementType next = b.lookAhead(1);
            if (next != null && BUILTIN_TYPES.contains(next)) {
                PsiBuilder.Marker ta = b.mark();
                b.advanceLexer(); // consume ':'
                b.advanceLexer(); // consume type keyword
                ta.done(TYPE_ANNOTATION);
            }
        }
        expect(b, EQUAL);
        parseExpression(b);
        if (b.getTokenType() == COLON) {
            parseFieldAccessor(b);
        }
        m.done(CONST_DECL);
    }

    /**
     * Parses an atomic variable declaration:
     *   atomic<int> counter = 0
     *   atomic<string> name
     *
     * Grammar:  ATOMIC  LT  IDENTIFIER  GT  IDENTIFIER  ( EQUAL expr )?
     */
    private void parseAtomicDecl(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, ATOMIC);
        // Optional type parameter:  <int>
        if (b.getTokenType() == LT) {
            b.advanceLexer(); // consume '<'
            if (b.getTokenType() == IDENTIFIER || BUILTIN_TYPES.contains(b.getTokenType()))
                b.advanceLexer(); // consume type name (cyan via syntax highlighter / annotator)
            expect(b, GT);    // consume '>'
        }
        // Variable name
        expect(b, IDENTIFIER);
        // Optional initialiser
        if (b.getTokenType() == EQUAL) {
            b.advanceLexer();
            parseExpression(b);
        }
        m.done(VAR_DECL);
    }

    /**
     * Parses a volatile variable declaration:
     *   volatile flag = true
     *   volatile var flag = true   ← 'var' is optional
     */
    private void parseVolatileDecl(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, VOLATILE);
        // Optional 'var' keyword:  volatile var name = ...
        if (b.getTokenType() == VAR) b.advanceLexer();
        // Variable name
        expect(b, IDENTIFIER);
        // Optional initialiser
        if (b.getTokenType() == EQUAL) {
            b.advanceLexer();
            parseExpression(b);
        }
        m.done(VAR_DECL);
    }

    /**
     * Parses:  COLON  accessor  (COMMA accessor)*
     * where accessor is GET or SET.
     *
     * Produces a FIELD_ACCESSOR node.
     */
    private void parseFieldAccessor(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, COLON);
        parseOneAccessor(b);
        while (b.getTokenType() == COMMA) {
            b.advanceLexer();      // consume ','
            parseOneAccessor(b);
        }
        m.done(FIELD_ACCESSOR);
    }

    /** Consumes a single GET or SET token, or emits an error. */
    private void parseOneAccessor(@NotNull PsiBuilder b) {
        IElementType tt = b.getTokenType();
        if (tt == GET || tt == SET) {
            b.advanceLexer();
        } else {
            b.error("Expected 'get' or 'set'");
            if (!b.eof()) b.advanceLexer();
        }
    }

    /**
     * Parses a function declaration with all modifier forms:
     *
     *   func name(params) { body }
     *   func name(s: string, n: int) -> string { body }   ← typed params + return type
     *   sync func name(params) { body }
     *   override core func name(params) { body }
     *   defer func name(params) { body }
     *   func name(params) : sync { body }
     */
    private void parseFuncDecl(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        // Consume any leading modifiers before the 'func' keyword.
        IElementType tt = b.getTokenType();
        while (tt == OVERRIDE || tt == CORE || tt == SYNC || tt == DEFER || tt == ASYNC) {
            b.advanceLexer();
            tt = b.getTokenType();
        }
        expect(b, FUNC);
        expect(b, IDENTIFIER);
        parseParamList(b);

        // Optional return type annotation:  -> TypeName
        if (b.getTokenType() == ARROW) {
            b.advanceLexer(); // consume '->'
            if (b.getTokenType() == IDENTIFIER || BUILTIN_TYPES.contains(b.getTokenType())) {
                b.advanceLexer(); // consume return type name (cyan via syntax highlighter)
            } else {
                b.error("Expected return type after '->'");
            }
        }

        // Trailing modifier:  func name() : sync { ... }
        // Peek past the colon to distinguish ': sync' from ': get'/': set' (field accessor).
        if (b.getTokenType() == COLON) {
            IElementType afterColon = b.lookAhead(1);
            if (afterColon == SYNC || afterColon == OVERRIDE || afterColon == CORE || afterColon == DEFER) {
                b.advanceLexer(); // consume ':'
                b.advanceLexer(); // consume modifier keyword
            }
        }
        parseBlock(b);
        m.done(FUNC_DECL);
    }

    private void parseClassDecl(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, CLASS);
        expect(b, IDENTIFIER);
        // Consume optional superclass: ': SuperclassName'
        if (b.getTokenType() == COLON) {
            b.advanceLexer(); // consume ':'
            if (b.getTokenType() == IDENTIFIER) {
                b.advanceLexer(); // consume superclass name
            } else {
                b.error("Expected superclass name after ':'");
            }
        }
        // Consume optional implements: 'implements Interface1, Interface2'
        if (b.getTokenType() == IMPLEMENTS) {
            b.advanceLexer(); // consume 'implements'
            while (b.getTokenType() == IDENTIFIER) {
                b.advanceLexer(); // consume interface name
                if (b.getTokenType() == COMMA) {
                    b.advanceLexer(); // consume ','
                }
            }
        }
        parseBlock(b);
        m.done(CLASS_DECL);
    }

    private void parseInterfaceDecl(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, INTERFACE);
        expect(b, IDENTIFIER);
        // Consume optional superinterface: ': SuperInterfaceName'
        if (b.getTokenType() == COLON) {
            b.advanceLexer(); // consume ':'
            if (b.getTokenType() == IDENTIFIER) {
                b.advanceLexer(); // consume superinterface name
            } else {
                b.error("Expected interface name after ':'");
            }
        }
        parseBlock(b);
        m.done(INTERFACE_DECL);
    }

    private void parseModuleDecl(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, MODULE);
        expect(b, IDENTIFIER);
        parseBlock(b);
        m.done(MODULE_DECL);
    }

    private void parseEnumDecl(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, ENUM);
        expect(b, IDENTIFIER);
        expect(b, LBRACE);
        while (b.getTokenType() != RBRACE && !b.eof()) {
            if (b.getTokenType() == IDENTIFIER) b.advanceLexer();
            else if (b.getTokenType() == COMMA) b.advanceLexer();
            else { b.error("Unexpected token in enum"); if (!b.eof()) b.advanceLexer(); break; }
        }
        expect(b, RBRACE);
        m.done(ENUM_DECL);
    }

    private void parseImport(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, IMPORT);
        if (b.getTokenType() == STRING || b.getTokenType() == IDENTIFIER) b.advanceLexer();
        m.done(IMPORT_STMT);
    }

    private void parseInclude(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, INCLUDE);
        expect(b, IDENTIFIER);
        expect(b, FROM);
        if (b.getTokenType() == STRING) b.advanceLexer();
        if (b.getTokenType() == INVOLVE) {
            b.advanceLexer();
            expect(b, LBRACE);
            while (b.getTokenType() != RBRACE && !b.eof()) {
                if (b.getTokenType() == STRING || b.getTokenType() == COMMA) b.advanceLexer();
                else b.advanceLexer();
            }
            expect(b, RBRACE);
        }
        m.done(INCLUDE_STMT);
    }

    private void parseIf(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, IF);
        boolean hasParen = b.getTokenType() == LPAREN;
        if (hasParen) b.advanceLexer();
        parseExpression(b);
        if (hasParen) expect(b, RPAREN);
        parseBlock(b);
        if (b.getTokenType() == ELSE) {
            b.advanceLexer();
            if (b.getTokenType() == IF) parseIf(b);
            else parseBlock(b);
        }
        m.done(IF_STMT);
    }

    private void parseWhile(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, WHILE);
        boolean hasParen = b.getTokenType() == LPAREN;
        if (hasParen) b.advanceLexer();
        parseExpression(b);
        if (hasParen) expect(b, RPAREN);
        parseBlock(b);
        m.done(WHILE_STMT);
    }

    private void parseFor(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, FOR);
        if (b.getTokenType() == LPAREN) {
            expect(b, LPAREN);
            if (b.getTokenType() != SEMICOLON) parseStatement(b);
            if (b.getTokenType() == SEMICOLON) b.advanceLexer();
            if (b.getTokenType() != SEMICOLON) parseExpression(b);
            if (b.getTokenType() == SEMICOLON) b.advanceLexer();
            if (b.getTokenType() != RPAREN)    parseStatement(b);
            expect(b, RPAREN);
            parseBlock(b);
            m.done(FOR_STMT);
        } else if (b.getTokenType() == IDENTIFIER) {
            expect(b, IDENTIFIER);
            expect(b, IN);
            parseExpression(b);
            parseBlock(b);
            m.done(FOREACH_STMT);
        } else {
            expect(b, LPAREN);
            expect(b, IDENTIFIER);
            expect(b, IN);
            parseExpression(b);
            expect(b, RPAREN);
            parseBlock(b);
            m.done(FOREACH_STMT);
        }
    }

    private void parseReturn(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, RETURN);
        if (b.getTokenType() != RBRACE && b.getTokenType() != null && !b.eof()) {
            parseExpression(b);
        }
        m.done(RETURN_STMT);
    }

    private void parsePrint(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer();
        expect(b, LPAREN);
        if (b.getTokenType() != RPAREN) parseExpression(b);
        expect(b, RPAREN);
        m.done(PRINT_STMT);
    }

    private void parseTryCatch(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, TRY);
        parseBlock(b);
        if (b.getTokenType() == CATCH) {
            b.advanceLexer();
            expect(b, LPAREN);
            if (b.getTokenType() == IDENTIFIER) b.advanceLexer();
            expect(b, RPAREN);
            parseBlock(b);
        }
        if (b.getTokenType() == FINALLY) {
            b.advanceLexer();
            parseBlock(b);
        }
        m.done(TRY_STMT);
    }

    private void parseThrow(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, THROW);
        parseExpression(b);
        m.done(THROW_STMT);
    }

    private void parseSwitch(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, SWITCH);
        parseExpression(b);
        expect(b, LBRACE);
        while (b.getTokenType() != RBRACE && !b.eof()) {
            if (b.getTokenType() == CASE) {
                b.advanceLexer();
                parseExpression(b);
                expect(b, COLON);
                parseBlock(b);
            } else if (b.getTokenType() == DEFAULT) {
                b.advanceLexer();
                expect(b, COLON);
                parseBlock(b);
            } else {
                b.advanceLexer();
            }
        }
        expect(b, RBRACE);
        m.done(SWITCH_STMT);
    }

    private void parseBlock(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, LBRACE);
        while (b.getTokenType() != RBRACE && !b.eof()) {
            parseStatement(b);
        }
        expect(b, RBRACE);
        m.done(BLOCK);
    }

    private void parseExprStatement(@NotNull PsiBuilder b) {
        if (b.eof()) return;
        PsiBuilder.Marker m = b.mark();
        parseExpression(b);
        m.done(EXPR_STMT);
    }

    /**
     * Parses a parameter list, supporting both plain and typed params:
     *   (a, b)                   ← plain
     *   (s: string, n: int)      ← typed  (name COLON type)
     *   (a, s: string, b)        ← mixed
     */
    private void parseParamList(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, LPAREN);
        while (b.getTokenType() != RPAREN && !b.eof()) {
            if (b.getTokenType() == IDENTIFIER) {
                b.advanceLexer(); // param name
                // Optional type annotation: ': typename'
                if (b.getTokenType() == COLON) {
                    b.advanceLexer(); // consume ':'
                    if (b.getTokenType() == IDENTIFIER || BUILTIN_TYPES.contains(b.getTokenType())) {
                        b.advanceLexer(); // consume type name (cyan via syntax highlighter / annotator)
                    } else {
                        b.error("Expected type name after ':'");
                    }
                }
            } else if (b.getTokenType() == COMMA) {
                b.advanceLexer();
            } else {
                b.error("Expected parameter");
                if (!b.eof()) b.advanceLexer();
                break;
            }
        }
        expect(b, RPAREN);
        m.done(PARAM_LIST);
    }

    private void parseExpression(@NotNull PsiBuilder b) { parseTernary(b); }

    private void parseTernary(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        parseLogical(b);
        if (b.getTokenType() == QUESTION) {
            b.advanceLexer();
            parseExpression(b);
            expect(b, COLON);
            parseExpression(b);
            m.done(TERNARY_EXPR);
        } else {
            m.drop();
        }
    }

    private void parseLogical(@NotNull PsiBuilder b) {
        PsiBuilder.Marker left = b.mark();
        parseEquality(b);
        while (b.getTokenType() == AND || b.getTokenType() == OR) {
            b.advanceLexer(); parseEquality(b);
            PsiBuilder.Marker done = left.precede(); left.drop(); left = done;
            left.done(BINARY_EXPR); left = b.mark();
        }
        left.drop();
    }

    private void parseEquality(@NotNull PsiBuilder b) {
        PsiBuilder.Marker left = b.mark();
        parseComparison(b);
        while (b.getTokenType() == EQEQ || b.getTokenType() == NOTEQ) {
            b.advanceLexer(); parseComparison(b);
            PsiBuilder.Marker done = left.precede(); left.drop(); left = done;
            left.done(BINARY_EXPR); left = b.mark();
        }
        left.drop();
    }

    private void parseComparison(@NotNull PsiBuilder b) {
        PsiBuilder.Marker left = b.mark();
        parseTerm(b);
        while (b.getTokenType() == LT || b.getTokenType() == GT
                || b.getTokenType() == LTE || b.getTokenType() == GTE) {
            b.advanceLexer(); parseTerm(b);
            PsiBuilder.Marker done = left.precede(); left.drop(); left = done;
            left.done(BINARY_EXPR); left = b.mark();
        }
        left.drop();
    }

    private void parseTerm(@NotNull PsiBuilder b) {
        PsiBuilder.Marker left = b.mark();
        parseFactor(b);
        while (b.getTokenType() == PLUS || b.getTokenType() == MINUS) {
            b.advanceLexer(); parseFactor(b);
            PsiBuilder.Marker done = left.precede(); left.drop(); left = done;
            left.done(BINARY_EXPR); left = b.mark();
        }
        left.drop();
    }

    private void parseFactor(@NotNull PsiBuilder b) {
        PsiBuilder.Marker left = b.mark();
        parseUnary(b);
        while (b.getTokenType() == STAR || b.getTokenType() == SLASH) {
            b.advanceLexer(); parseUnary(b);
            PsiBuilder.Marker done = left.precede(); left.drop(); left = done;
            left.done(BINARY_EXPR); left = b.mark();
        }
        left.drop();
    }

    private void parseUnary(@NotNull PsiBuilder b) {
        if (b.getTokenType() == MINUS || b.getTokenType() == BANG) {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            parseUnary(b);
            m.done(UNARY_EXPR);
        } else if (b.getTokenType() == AWAIT) {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            parseUnary(b);
            m.done(AWAIT_EXPR);
        } else {
            parsePostfix(b);
        }
    }

    private void parsePostfix(@NotNull PsiBuilder b) {
        PsiBuilder.Marker left = b.mark();
        parsePrimary(b);
        while (true) {
            IElementType tt = b.getTokenType();
            if (tt == LPAREN) {
                parseArgList(b);
                PsiBuilder.Marker done = left.precede(); left.drop(); left = done;
                left.done(CALL_EXPR); left = b.mark();
            } else if (tt == DOT) {
                b.advanceLexer();
                if (b.getTokenType() == IDENTIFIER) b.advanceLexer();
                PsiBuilder.Marker done = left.precede(); left.drop(); left = done;
                left.done(GET_EXPR); left = b.mark();
            } else if (tt == LBRACKET) {
                b.advanceLexer();
                parseExpression(b);
                expect(b, RBRACKET);
                PsiBuilder.Marker done = left.precede(); left.drop(); left = done;
                left.done(INDEX_EXPR); left = b.mark();
            } else if (tt == EQUAL || tt == PLUS_EQUAL || tt == MINUS_EQUAL
                    || tt == STAR_EQUAL || tt == SLASH_EQUAL) {
                b.advanceLexer();
                parseExpression(b);
                PsiBuilder.Marker done = left.precede(); left.drop(); left = done;
                left.done(ASSIGN_STMT); left = b.mark();
            } else if (tt == BANG) {
                b.advanceLexer();
                PsiBuilder.Marker done = left.precede(); left.drop(); left = done;
                left.done(NON_NULL_EXPR); left = b.mark();
            } else {
                break;
            }
        }
        left.drop();
    }

    private void parsePrimary(@NotNull PsiBuilder b) {
        IElementType tt = b.getTokenType();
        if (tt == null || b.eof()) return;

        if (tt == NUMBER || tt == STRING || tt == RAW_STRING
                || tt == NIL || tt == TRUE || tt == FALSE) {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            m.done(LITERAL_EXPR);
            return;
        }
        if (tt == IDENTIFIER) {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            m.done(VAR_EXPR);
            return;
        }
        if (tt == THIS) { b.advanceLexer(); return; }
        if (tt == NEW) {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            expect(b, IDENTIFIER);
            parseArgList(b);
            m.done(NEW_EXPR);
            return;
        }
        if (tt == LPAREN) {
            b.advanceLexer();
            parseExpression(b);
            expect(b, RPAREN);
            return;
        }
        if (tt == LBRACKET) {
            PsiBuilder.Marker m = b.mark();
            b.advanceLexer();
            while (b.getTokenType() != RBRACKET && !b.eof()) {
                parseExpression(b);
                if (b.getTokenType() == COMMA) b.advanceLexer();
            }
            expect(b, RBRACKET);
            m.done(ARRAY_EXPR);
            return;
        }

        b.error("Unexpected token");
        b.advanceLexer();
    }

    private void parseArgList(@NotNull PsiBuilder b) {
        PsiBuilder.Marker m = b.mark();
        expect(b, LPAREN);
        while (b.getTokenType() != RPAREN && !b.eof()) {
            int before = b.getCurrentOffset();
            parseExpression(b);
            if (b.getTokenType() == COMMA) b.advanceLexer();
            if (b.getCurrentOffset() == before && !b.eof()) b.advanceLexer();
        }
        expect(b, RPAREN);
        m.done(ARG_LIST);
    }

    private void expect(@NotNull PsiBuilder b, IElementType type) {
        if (b.getTokenType() == type) {
            b.advanceLexer();
        } else {
            b.error("Expected " + type);
            if (!b.eof()) b.advanceLexer(); // always advance to prevent infinite loops
        }
    }
}