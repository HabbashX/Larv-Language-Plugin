package com.habbashx.larv.plugin.highlighting;

import com.habbashx.larv.plugin.lang.LarvIcons;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public final class LarvColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] ATTRS = {
            new AttributesDescriptor("Keyword",        LarvSyntaxHighlighter.KEYWORD),
            new AttributesDescriptor("String",         LarvSyntaxHighlighter.STRING_KEY),
            new AttributesDescriptor("Number",         LarvSyntaxHighlighter.NUMBER_KEY),
            new AttributesDescriptor("Comment",        LarvSyntaxHighlighter.COMMENT_KEY),
            new AttributesDescriptor("Operator",       LarvSyntaxHighlighter.OPERATOR_KEY),
            new AttributesDescriptor("Identifier",     LarvSyntaxHighlighter.IDENTIFIER_KEY),
            new AttributesDescriptor("Braces",         LarvSyntaxHighlighter.BRACES_KEY),
            new AttributesDescriptor("Brackets",       LarvSyntaxHighlighter.BRACKETS_KEY),
            new AttributesDescriptor("Parentheses",    LarvSyntaxHighlighter.PARENS_KEY),
            new AttributesDescriptor("Comma",          LarvSyntaxHighlighter.COMMA_KEY),
            new AttributesDescriptor("Dot",            LarvSyntaxHighlighter.DOT_KEY),
            new AttributesDescriptor("Semicolon",      LarvSyntaxHighlighter.SEMICOLON_KEY),
            new AttributesDescriptor("Bad Character",  LarvSyntaxHighlighter.BAD_CHAR_KEY),
            new AttributesDescriptor("Class Constant", LarvSyntaxHighlighter.CLASS_CONST_KEY),
            new AttributesDescriptor("Parameter",      LarvSyntaxHighlighter.PARAM_KEY),
            new AttributesDescriptor("Type Reference (cyan)", LarvSyntaxHighlighter.TYPE_REF_KEY),
    };

    @Override public @NotNull String getDisplayName()           { return "Larv"; }
    @Override public @Nullable Icon  getIcon()                  { return LarvIcons.FILE; }
    @Override public @NotNull SyntaxHighlighter getHighlighter() { return new LarvSyntaxHighlighter(); }

    @Override
    public @NotNull String getDemoText() {
        return """
                // Larv language demo
                module MathUtils {
                    func add(a, b) {
                        return a + b
                    }
                }

                // New-style typed function signatures
                func greet(name: string, count: int) -> string {
                    return name
                }

                // atomic and volatile concurrency modifiers
                atomic<int> counter = 0
                volatile flag = true

                // defer runs cleanup at end of scope
                func readFile(path: string) -> string {
                    defer closeFile(path)
                    return readAll(path)
                }

                // sync function modifier
                sync func increment() {
                    counter++
                }

                class Animal {
                    func init(name: string) {
                        this.name = name
                    }
                    func speak() -> string {
                        return "I am " + this.name
                    }
                }

                enum Direction { NORTH, SOUTH, EAST, WEST }

                const PI = 3.14159
                var x = 0

                for i in range(10) {
                    x += i
                }

                try {
                    var result = 10 / 0
                } catch (e) {
                    printErr("error: " + e)
                } finally {
                    print("done")
                }
                """;
    }

    @Override public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() { return null; }
    @Override public AttributesDescriptor @NotNull [] getAttributeDescriptors()  { return ATTRS; }
    @Override public ColorDescriptor @NotNull []      getColorDescriptors()      { return ColorDescriptor.EMPTY_ARRAY; }
}