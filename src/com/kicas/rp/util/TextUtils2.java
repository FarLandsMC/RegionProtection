package com.kicas.rp.util;

import com.google.gson.Gson;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class TextUtils2 {
    private static final char ESCAPE_CHAR = '\\';
    private static final char VALUE_MARKER = '%';
    private static final char QUOTE_MARKER = '#';
    private static final char COLOR_CHAR = '&';
    private static final char SECTION_START = '{';
    private static final char SECTION_END = '}';
    private static final char FUNCTION_CHAR = '$';

    // Component types
    private static final int COMPONENT_NORMAL = 0;
    private static final int COMPONENT_EVENT = 1;

    // Parser states
    private static final int ADD_TEXT = 0;
    private static final int FORCE_ADD = 1;
    private static final int COLOR_START = 2;
    private static final int COLOR_BUILD_FIRST = 3;
    private static final int COLOR_BUILD_EXTRA = 4;
    private static final int EVENT_START = 5;
    private static final int EVENT_BUILD_TYPE = 6;
    private static final int EVENT_BUILD_NAME = 7;
    private static final int EVENT_BUILD_ARG = 8;
    private static final int QUOTING = 9;

    // Internal errors
    private static final String INDEX_STACK_ERROR = "index stack empty";
    private static final String TOKEN_STACK_ERROR = "token stack empty";
    private static final String COMPONENT_STACK_ERROR = "component stack empty";
    private static final String CHILD_STACK_ERROR = "children stack empty";

    /**
     * Parses the given input text and substitutes in the given values and sends the result to the given command sender.
     *
     * @param sender the recipient of the formatted message.
     * @param input  the input text.
     * @param values the values to substitute in.
     */
    public static void sendFormatted(CommandSender sender, String input, Object... values) throws ParserError {
        sender.spigot().sendMessage(format(input, values));
    }

    /**
     * Parses the given input text and substitutes in the given values and sends the result to the given player as the
     * given message type.
     *
     * @param player the recipient of the formatted message.
     * @param type   the message type.
     * @param input  the input text.
     * @param values the values to substitute in.
     */
    public static void sendFormatted(Player player, ChatMessageType type, String input, Object... values) throws ParserError {
        player.spigot().sendMessage(type, format(input, values));
    }

    /**
     * Parses the given input text and substitutes in the given values and returns the result as an array of base
     * components.
     *
     * @param input  the input text.
     * @param values the values to substitute in.
     * @return the parsed input text as an array of base components.
     */
    public static BaseComponent[] format(String input, Object... values) throws ParserError {
        return parse(insertValues(input, values), values);
    }

    /**
     * Parses the given input text and returns the result as an array of base components.
     *
     * @param input the input text.
     * @return the parsed input text as an array of base components.
     */
    public static BaseComponent[] format(String input) throws ParserError {
        return parse(input, null);
    }

    /**
     * Inserts the given values into the raw string where the sequence %x occurs where x is the hexadecimal index of the
     * value to insert.
     *
     * @param raw    the raw string.
     * @param values the values to insert.
     * @return the string with the values inserted.
     */
    public static String insertValues(String raw, Object... values) {
        if (values.length == 0)
            return raw;

        StringBuilder sb = new StringBuilder(raw.length());
        char[] chars = raw.toCharArray();
        char cur, next;

        for (int i = 0; i < chars.length; ++i) {
            cur = chars[i];
            next = i < chars.length - 1 ? chars[i + 1] : '\0';

            // Ignore escaped characters
            if (cur == '\\' && next == VALUE_MARKER) {
                sb.append(VALUE_MARKER);
                ++i;
            }
            // Insert a value
            else if (cur == VALUE_MARKER) {
                // Use hex for more indices
                int index = Character.digit(next, 16);
                if (index < values.length)
                    sb.append(values[index]);
                ++i;
            }
            // Just append the next character
            else sb.append(cur);
        }

        return sb.toString();
    }

    private static BaseComponent[] parse(String cfmt, Object[] values) throws ParserError {
        Parser parser = new Parser(cfmt);

        int state = ADD_TEXT;
        int componentType = COMPONENT_NORMAL;
        // Whether or not a formatting code was negated
        boolean applyFormat = true;
        // Keep track of bracket pairs
        int curlyBracketDepth = 0;

        for (char ch : cfmt.toCharArray()) {
            switch (state) {
                case ADD_TEXT: {
                    switch (ch) {
                        case ESCAPE_CHAR:
                            state = FORCE_ADD;
                            break;

                        case QUOTE_MARKER:
                            state = QUOTING;
                            break;

                        case COLOR_CHAR:
                            parser.finishComponent();
                            state = COLOR_START;
                            parser.tokenStack.push(new StringBuffer());
                            parser.mark();
                            break;

                        case SECTION_START: {
                            curlyBracketDepth += 1;
                            TextComponent next = new TextComponent();
                            if (parser.hasChildren.last()) {
                                next.copyFormatting(parser.stack.last().inner, ComponentBuilder.FormatRetention.FORMATTING, true);
                            }
                            parser.stack.push(new BuildableTextComponent(next));
                            parser.hasChildren.push(false);
                            parser.mark();
                            break;
                        }

                        case SECTION_END: {
                            if (curlyBracketDepth == 0) {
                                parser.mark();
                                parser.error("Unpaired curly bracket: \"%s...\"", 10);
                            }

                            curlyBracketDepth -= 1;

                            if (parser.hasChildren.pop()) {
                                parser.collapse();
                            }

                            TextComponent block = parser.popStack();
                            BuildableTextComponent next = new BuildableTextComponent(), last;

                            if (parser.hasChildren.last()) {
                                TextComponent reference = parser.popStack();
                                next.inner.copyFormatting(reference, ComponentBuilder.FormatRetention.FORMATTING, true);
                                last = parser.stack.last();
                                addChild(last.inner, reference);
                            } else {
                                last = parser.stack.last();
                                parser.hasChildren.setLast(true);
                            }

                            addChild(last.inner, block);
                            parser.stack.push(next);
                            break;
                        }

                        case FUNCTION_CHAR:
                            if (componentType == COMPONENT_EVENT) {
                                parser.error("Events cannot be nested within components attached to events.");
                            }

                            parser.finishComponent();
                            state = EVENT_START;
                            parser.mark();
                            break;

                        case ')':
                            if (componentType == COMPONENT_EVENT) {
                                parser.finishEvent(true);
                                state = ADD_TEXT;
                                componentType = COMPONENT_NORMAL;
                                parser.unmark();
                            } else {
                                parser.stack.last().text.append(ch);
                            }
                            break;

                        default:
                            parser.stack.last().text.append(ch);
                    }
                    break;
                }

                case FORCE_ADD:
                    parser.stack.last().text.append(ch);
                    break;

                case COLOR_START: {
                    if (ch == '(') {
                        state = COLOR_BUILD_FIRST;
                        parser.mark(1);
                    } else
                        parser.error("Expected open parenthesis after '&': \"%s...\"", 10);
                    break;
                }

                case COLOR_BUILD_FIRST:
                case COLOR_BUILD_EXTRA: {
                    if (ch == ',' || ch == ')') {
                        String token = parser.tokenStack.pop().toString();

                        if (ch == ')' && token.isEmpty()) {
                            parser.unmark();
                            parser.error(
                                    "Dangling comma at the end of formatting sequence: \"%s\"",
                                    parser.index - parser.indexStack.last() + 1
                            );
                        }

                        BuildableTextComponent component = parser.stack.last();

                        if (state == COLOR_BUILD_FIRST) {
                            ChatColor color = parseChatColor(token);
                            if (color != null) {
                                if (color.getColor() != null) {
                                    if (!applyFormat) {
                                        parser.error(
                                                "Negation character not allowed in front of a color code: \"%s\"",
                                                parser.index - parser.indexStack.last()
                                        );
                                    }

                                    component.inner.setColor(color);
                                    token = "";
                                }
                            } else {
                                parser.error(
                                        "Invalid color code: \"%s\"",
                                        parser.index - parser.indexStack.last()
                                );
                            }
                        }

                        if (!token.isEmpty()) {
                            switch (token) {
                                case "obfuscated":
                                    component.inner.setObfuscated(applyFormat);
                                    break;

                                case "bold":
                                    component.inner.setBold(applyFormat);
                                    break;

                                case "strikethrough":
                                    component.inner.setStrikethrough(applyFormat);
                                    break;

                                case "underline":
                                    component.inner.setUnderlined(applyFormat);
                                    break;

                                case "italic":
                                    component.inner.setItalic(applyFormat);
                                    break;

                                default: {
                                    ChatColor color = parseChatColor(token);
                                    if (color == null) {
                                        parser.error(
                                                "Invalid color or formatting code: \"%s\"",
                                                parser.index - parser.indexStack.last()
                                        );
                                    } else {
                                        parser.unmark();
                                        parser.error(
                                                "Excpected color or \"reset\" as first argument of color sequence: \"%s...\"",
                                                parser.index - parser.indexStack.last()
                                        );
                                    }
                                }
                            }

                            applyFormat = true;
                        }

                        parser.unmark();

                        if (ch == ',') {
                            parser.mark(1);
                            parser.tokenStack.push(new StringBuffer());
                        } else {
                            parser.unmark();
                            state = ADD_TEXT;
                        }
                    } else if (ch == '!') {
                        if (parser.currentToken().length() == 0) {
                            applyFormat = false;
                        } else if (applyFormat) {
                            parser.error(
                                    "Expected negation character ('!') to be at the beginning of a formatting code: \"%s...\"",
                                    parser.index - parser.indexStack.last() + 3
                            );
                        } else {
                            parser.currentToken().append(ch);
                        }
                    } else {
                        parser.currentToken().append(ch);
                    }
                    break;
                }

                case EVENT_START: {
                    if (ch == '(') {
                        state = EVENT_BUILD_TYPE;
                        parser.tokenStack.push(new StringBuffer());
                        parser.mark(1);
                    } else {
                        parser.error("Expected open parenthesis after '$': \"%s...\"", 10);
                    }
                    break;
                }

                case EVENT_BUILD_TYPE: {
                    if (ch == ':') {
                        StringBuffer eventType = parser.currentToken();
                        if ("hover".contentEquals(eventType) || "click".contentEquals(eventType)) {
                            parser.tokenStack.push(new StringBuffer());
                            state = EVENT_BUILD_NAME;
                            parser.unmark();
                            parser.mark(1);
                        } else {
                            parser.error(
                                    "Invalid event type, expected \"hover\" or \"click\" but found \"%s\"",
                                    parser.index - parser.indexStack.last()
                            );
                        }
                    } else {
                        parser.currentToken().append(ch);
                    }
                    break;
                }

                case EVENT_BUILD_NAME: {
                    if (ch == ',') {
                        StringBuffer eventType = parser.tokenStack.inner.elementAt(parser.tokenStack.inner.size() - 2);
                        StringBuffer eventName = parser.tokenStack.last();

                        if ("hover".contentEquals(eventType)) {
                            if ("show_text".contentEquals(eventName)) {
                                state = ADD_TEXT;
                                componentType = COMPONENT_EVENT;
                                parser.stack.push(new BuildableTextComponent());
                                parser.hasChildren.push(false);
                                continue;
                            } else if ("show_item".contentEquals(eventName) || "show_entity".contentEquals(eventName)) {
                                state = EVENT_BUILD_ARG;
                            } else {
                                parser.error(
                                        "Invalid event name for hover type: \"%s\"",
                                        parser.index - parser.indexStack.last()
                                );
                            }
                        } else if ("click".contentEquals(eventType)) {
                            if (ClickEvent.Action.valueOf(eventName.toString().toUpperCase()) != null) {
                                state = EVENT_BUILD_ARG;
                            } else {
                                parser.error(
                                        "Invalid event name for click type: \"%s\"",
                                        parser.index - parser.indexStack.last()
                                );
                            }
                        }

                        parser.tokenStack.push(new StringBuffer());
                        parser.unmark();
                        parser.mark(1);
                    } else {
                        parser.currentToken().append(ch);
                    }
                    break;
                }

                case EVENT_BUILD_ARG: {
                    if (ch == ')') {
                        parser.finishEvent(false);
                        state = ADD_TEXT;
                        parser.unmark();
                    } else {
                        parser.currentToken().append(ch);
                    }
                    break;
                }

                case QUOTING: {
                    int index = Character.digit(ch, 16);
                    if (values != null && index < values.length)
                        parser.stack.last().text.append(values[index]);
                    break;
                }
            }

            parser.index += 1;
        }

        switch (state) {
            case ADD_TEXT: {
                if (componentType == COMPONENT_EVENT) {
                    parser.error("Incomplete event sequence at the end of the input string.");
                }

                // Check to make sure all open brackets are matched
                if (curlyBracketDepth > 0) {
                    parser.error("Unpaired curly bracket: \"%s...\"", 10);
                }

                // Collapse the stack into a single component
                while (parser.stack.inner.size() > 1) {
                    parser.collapse();
                }

                return new BaseComponent[] {parser.popStack()};
            }

            case FORCE_ADD: {
                parser.error("Expected another character after the escape character at the end of the input string.");
            }

            case COLOR_START:
            case COLOR_BUILD_FIRST:
            case COLOR_BUILD_EXTRA: {
                parser.error("Incomplete color sequence at the end of the input string.");
            }

            case EVENT_START:
            case EVENT_BUILD_TYPE:
            case EVENT_BUILD_NAME:
            case EVENT_BUILD_ARG: {
                parser.error("Incomplete event sequence at the end of the input string.");
            }
        }

        return new BaseComponent[] {parser.popStack()};
    }

    private static ChatColor parseChatColor(String color) {
        try {
            return ChatColor.of(color);
        } catch (Throwable unused) {
            return null;
        }
    }

    private static void addChild(TextComponent parent, TextComponent child) {
        List<BaseComponent> extra = parent.getExtra();
        if (extra != null && !extra.isEmpty()) {
            TextComponent prevChild = (TextComponent) extra.get(extra.size() - 1);
            if (prevChild.getText().trim().isEmpty() && !child.isUnderlinedRaw() && !child.isStrikethroughRaw()) {
                child.setText(prevChild.getText() + child.getText());
                extra.remove(extra.size() - 1);
            }
        }

        parent.addExtra(child);
    }

    private static class Parser {
        String cfmt;
        TextUtils2.Stack<BuildableTextComponent> stack;

        // Current character index
        int index;

        // Current token being built
        TextUtils2.Stack<StringBuffer> tokenStack;

        // Whether or not the component at the current depth according to the variable above
        // has any children appended to it
        TextUtils2.Stack<Boolean> hasChildren;

        // Keep track of indices where errors could occur
        TextUtils2.Stack<Integer> indexStack;

        Parser(String cfmt) {
            this.cfmt = cfmt;
            stack = new TextUtils2.Stack<>(COMPONENT_STACK_ERROR);
            index = 0;
            tokenStack = new TextUtils2.Stack<>(TOKEN_STACK_ERROR);
            hasChildren = new TextUtils2.Stack<>(CHILD_STACK_ERROR);
            indexStack = new TextUtils2.Stack<>(INDEX_STACK_ERROR);

            stack.push(new BuildableTextComponent());
            hasChildren.push(false);
        }

        TextComponent popStack() throws ParserError {
            return stack.pop().build();
        }

        void error(String format, int length) throws ParserError {
            int idx = indexStack.last();

            int right = idx + Math.min(length, 35);
            if (right > cfmt.length()) {
                right = cfmt.length();
            }

            throw new ParserError(String.format(format, cfmt.substring(idx, right)));
        }

        void error(String message) throws ParserError {
            throw new ParserError(message);
        }

        void mark(int offset) {
            indexStack.push(index + offset);
        }

        void mark() {
            indexStack.push(index);
        }

        void unmark() {
            if (!indexStack.inner.isEmpty())
                indexStack.inner.pop();
        }

        StringBuffer currentToken() throws ParserError {
            return tokenStack.last();
        }

        void collapse() throws ParserError {
            TextComponent child = popStack();
            if (!child.getText().isEmpty()) {
                addChild(stack.last().inner, child);
            }
        }

        void finishComponent() throws ParserError {
            if (stack.last().text.length() != 0) {
                if (hasChildren.last()) {
                    TextComponent child = popStack();
                    TextComponent next = new TextComponent();
                    next.copyFormatting(child, ComponentBuilder.FormatRetention.FORMATTING, true);
                    addChild(stack.last().inner, child);
                    stack.push(new BuildableTextComponent(next));
                } else {
                    stack.push(new BuildableTextComponent());
                    hasChildren.setLast(true);
                }
            }
        }

        void finishEvent(boolean hasComponent) throws ParserError {
            if (hasComponent) {
                if (hasChildren.pop()) {
                    TextComponent child = popStack();
                    addChild(stack.last().inner, child);
                }

                TextComponent text = popStack();
                stack.last().inner.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new BaseComponent[] {text})));
            } else {
                String eventArg = tokenStack.pop().toString();
                String eventName = tokenStack.pop().toString();
                String eventType = tokenStack.pop().toString();
                BuildableTextComponent component = stack.last();

                if ("hover".equals(eventType)) {
                    if ("show_item".equals(eventName)) {
                        try {
                            component.inner.setHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_ITEM,
                                    new Gson().fromJson(eventArg, TextUtils2.Item.class).into()
                            ));
                        } catch (Throwable unused) {
                            throw new ParserError("Invalid item for \"show_item\" hover event.");
                        }
                    } else if ("show_entity".equals(eventName)) {
                        try {
                            component.inner.setHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_ENTITY,
                                    new Gson().fromJson(eventArg, TextUtils2.Entity.class).into()
                            ));
                        } catch (Throwable unused) {
                            throw new ParserError("Invalid item for \"show_entity\" hover event.");
                        }
                    }
                } else {
                    // Checks are done beforehand to ensure that this doesn't fail
                    ClickEvent.Action action = ClickEvent.Action.valueOf(eventName.toUpperCase());
                    component.inner.setClickEvent(new ClickEvent(action, eventArg));
                }
            }
        }
    }

    private static class BuildableTextComponent {
        TextComponent inner;
        StringBuffer text;

        BuildableTextComponent() {
            inner = new TextComponent();
            text = new StringBuffer();
        }

        BuildableTextComponent(TextComponent inner) {
            this.inner = inner;
            text = new StringBuffer();
        }

        TextComponent build() {
            inner.setText(text.toString());
            return inner;
        }
    }

    public static class ParserError extends Exception {
        ParserError(String msg) {
            super(msg);
        }
    }

    private static class Stack<E> {
        java.util.Stack<E> inner;
        String error;

        Stack(String error) {
            inner = new java.util.Stack<>();
            this.error = error;
        }

        void push(E element) {
            inner.push(element);
        }

        E pop() throws ParserError {
            if (inner.isEmpty())
                throw new ParserError("Internal parser error: " + error);
            return inner.pop();
        }

        E last() throws ParserError {
            if (inner.isEmpty())
                throw new ParserError("Internal parser error: " + error);
            return inner.lastElement();
        }

        void setLast(E value) throws ParserError {
            if (inner.isEmpty())
                throw new ParserError("Internal parser error: " + error);
            inner.set(inner.size() - 1, value);
        }
    }

    private static class Item {
        String id;
        int count;
        String tag;

        Item() {
            id = "";
            count = 1;
            tag = null;
        }

        net.md_5.bungee.api.chat.hover.content.Item into() {
            return new net.md_5.bungee.api.chat.hover.content.Item(id, count, ItemTag.ofNbt(tag));
        }
    }

    private static class Entity {
        String id;
        String type;
        String name;

        Entity() {
            id = "";
            type = null;
            name = null;
        }

        net.md_5.bungee.api.chat.hover.content.Entity into() throws TextUtils2.ParserError {
            return new net.md_5.bungee.api.chat.hover.content.Entity(
                    type,
                    id,
                    name == null ? null : TextUtils2.parse(name, null)[0]
            );
        }
    }
}
