package com.habbashx.larv.plugin.formatting;

import com.habbashx.larv.plugin.lexer.LarvTokenTypes;
import com.habbashx.larv.plugin.parser.LarvElementTypes;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class LarvFormattingModelBuilder implements FormattingModelBuilder {

    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext ctx) {
        CodeStyleSettings settings = ctx.getCodeStyleSettings();
        SpacingBuilder spacing = createSpacing(settings);
        return FormattingModelProvider.createFormattingModelForPsiFile(
                ctx.getContainingFile(),
                new LarvBlock(ctx.getNode(), null, Indent.getNoneIndent(), null, spacing),
                settings);
    }

    private SpacingBuilder createSpacing(CodeStyleSettings settings) {
        return new SpacingBuilder(settings, com.habbashx.larv.plugin.lang.LarvLanguage.INSTANCE)
                .after(LarvTokenTypes.IF).spaces(1)
                .after(LarvTokenTypes.WHILE).spaces(1)
                .after(LarvTokenTypes.FOR).spaces(1)
                .after(LarvTokenTypes.RETURN).spaces(1)
                .after(LarvTokenTypes.THROW).spaces(1)
                .after(LarvTokenTypes.VAR).spaces(1)
                .after(LarvTokenTypes.CONST).spaces(1)
                .after(LarvTokenTypes.FUNC).spaces(1)
                .after(LarvTokenTypes.CLASS).spaces(1)
                .after(LarvTokenTypes.MODULE).spaces(1)
                .after(LarvTokenTypes.ENUM).spaces(1)
                .after(LarvTokenTypes.NEW).spaces(1)
                .around(LarvTokenTypes.EQUAL).spaces(1)
                .around(LarvTokenTypes.PLUS_EQUAL).spaces(1)
                .around(LarvTokenTypes.MINUS_EQUAL).spaces(1)
                .around(LarvTokenTypes.STAR_EQUAL).spaces(1)
                .around(LarvTokenTypes.SLASH_EQUAL).spaces(1)
                .around(LarvTokenTypes.EQEQ).spaces(1)
                .around(LarvTokenTypes.NOTEQ).spaces(1)
                .around(LarvTokenTypes.LT).spaces(1)
                .around(LarvTokenTypes.GT).spaces(1)
                .around(LarvTokenTypes.LTE).spaces(1)
                .around(LarvTokenTypes.GTE).spaces(1)
                .around(LarvTokenTypes.AND).spaces(1)
                .around(LarvTokenTypes.OR).spaces(1)
                .around(LarvTokenTypes.PLUS).spaces(1)
                .around(LarvTokenTypes.MINUS).spaces(1)
                .around(LarvTokenTypes.STAR).spaces(1)
                .around(LarvTokenTypes.SLASH).spaces(1)
                .before(LarvTokenTypes.COMMA).none()
                .after(LarvTokenTypes.COMMA).spaces(1)
                .before(LarvElementTypes.BLOCK).spaces(1)
                // No space before paren in call
                .before(LarvTokenTypes.LPAREN).none()
                .after(LarvTokenTypes.LPAREN).none()
                .before(LarvTokenTypes.RPAREN).none()
                // No space inside brackets
                .after(LarvTokenTypes.LBRACKET).none()
                .before(LarvTokenTypes.RBRACKET).none()
                // Colon in switch
                .before(LarvTokenTypes.COLON).none()
                .after(LarvTokenTypes.COLON).spaces(1);
    }


    static final class LarvBlock extends AbstractBlock {

        private final SpacingBuilder spacing;
        private final Indent myIndent;

        LarvBlock(@NotNull ASTNode node,
                  @Nullable Wrap wrap,
                  @Nullable Indent indent,
                  @Nullable Alignment alignment,
                  @NotNull SpacingBuilder spacing) {
            super(node, wrap, alignment);
            this.spacing = spacing;
            this.myIndent = indent != null ? indent : Indent.getNoneIndent();
        }

        @Override
        public @NotNull Indent getIndent() {
            return myIndent;
        }

        @Override
        protected List<Block> buildChildren() {
            List<Block> result = new ArrayList<>();
            ASTNode child = myNode.getFirstChildNode();
            while (child != null) {
                if (child.getElementType() != com.intellij.psi.TokenType.WHITE_SPACE) {
                    Indent indent = computeChildIndent(child.getElementType());
                    result.add(new LarvBlock(child, null, indent, null, spacing));
                }
                child = child.getTreeNext();
            }
            return result;
        }

        private Indent computeChildIndent(IElementType type) {
            IElementType parent = myNode.getElementType();
            if (parent == LarvElementTypes.BLOCK) {
                if (type == LarvTokenTypes.LBRACE || type == LarvTokenTypes.RBRACE) {
                    return Indent.getNoneIndent();
                }
                return Indent.getNormalIndent();
            }
            return Indent.getNoneIndent();
        }

        @Override
        public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
            return spacing.getSpacing(this, child1, child2);
        }

        /**
         * Must NOT return null — IntelliJ throws NullPointerException on backspace/Enter
         * when this returns null, because the formatter calls getChildAttributes() to
         * compute indentation for the new line during typing.
         */
        @Override
        public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
            IElementType type = myNode.getElementType();
            if (type == LarvElementTypes.BLOCK) {
                return new ChildAttributes(Indent.getNormalIndent(), null);
            }
            return new ChildAttributes(Indent.getNoneIndent(), null);
        }

        @Override
        public boolean isIncomplete() {
            IElementType type = myNode.getElementType();
            if (type == LarvElementTypes.BLOCK) {
                ASTNode lastChild = myNode.getLastChildNode();
                return lastChild == null || lastChild.getElementType() != LarvTokenTypes.RBRACE;
            }
            return false;
        }

        @Override
        public boolean isLeaf() {
            return myNode.getFirstChildNode() == null;
        }
    }
}