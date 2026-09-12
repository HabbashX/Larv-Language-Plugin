package com.habbashx.larv.syntax.parser.rule;

import com.habbashx.larv.syntax.parser.ast.expression.Expression;
import com.habbashx.larv.syntax.parser.stream.TokenStream;

/**
 * Parses an expression that <em>begins</em> with a particular token type.
 *
 * <p>Examples: a number literal, a string literal, {@code this}, {@code new},
 * or an identifier — every atom that can legally appear at the start of an
 * expression has exactly one {@code PrefixRule} registered for its token type.</p>
 *
 * <p>The leading token has already been consumed by the time {@link #parse()}
 * is called, so implementations read it via {@link TokenStream#previous()}.</p>
 */
@FunctionalInterface
public interface PrefixRule {
    Expression parse();
}
