package com.habbashx.larv.syntax.parser.ast.statement;

import com.habbashx.larv.syntax.parser.ast.expression.Expression;
public record AtomicStatement(String type, String name, Expression initializer, int line) implements Statement {
}