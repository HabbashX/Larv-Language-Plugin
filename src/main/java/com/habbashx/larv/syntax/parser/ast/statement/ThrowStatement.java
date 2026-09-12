package com.habbashx.larv.syntax.parser.ast.statement;

import com.habbashx.larv.syntax.parser.ast.expression.Expression;

/**
 * AST node for a {@code throw} statement.
 *
 * <pre>
 *   throw "Something went wrong"
 *   throw errorCode
 * </pre>
 */
public record ThrowStatement(Expression value, int line) implements Statement {}
