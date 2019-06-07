package com.kicas.rp.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TextUtils {
    private static final char VALUE_MARKER = '%';
    private static final char COLOR_CHAR = '&';
    private static final char SECTION_START = '{';
    private static final char FUNCTION_CHAR = '$';

    public static void sendFormatted(CommandSender sender, String input, Object... values) {
        sender.spigot().sendMessage(format(input, values));
    }

    public static BaseComponent[] format(String input, Object... values) {
        return parseExpression(new Pair<>(ChatColor.WHITE, new ArrayList<>()), insertValues(input, values), values);
    }

    public static BaseComponent[] format(String input) {
        return parseExpression(new Pair<>(ChatColor.WHITE, new ArrayList<>()), input);
    }

    // Insert values into the raw inpuit
    public static String insertValues(String raw, Object... values) {
        if(values.length == 0)
            return raw;

        StringBuilder sb = new StringBuilder(raw.length());
        char[] chars = raw.toCharArray();
        char cur, next;
        for(int i = 0;i < chars.length;++ i) {
            cur = chars[i];
            next = i < chars.length - 1 ? chars[i + 1] : '\0';
            if(cur == '\\' && next == VALUE_MARKER) {
                sb.append(VALUE_MARKER);
                ++ i;
            }else if(cur == VALUE_MARKER) {
                int index = Character.digit(next, 10);
                if(index < values.length)
                    sb.append(values[index]);
                ++ i;
            }else
                sb.append(cur);
        }

        return sb.toString();
    }

    private static BaseComponent[] parseExpression(Pair<ChatColor, List<ChatColor>> format, String input, Object... values) {
        StringBuilder component = new StringBuilder();
        List<BaseComponent> expr = new ArrayList<>();
        char[] chars = input.toCharArray();
        char cur, next;

        for(int i = 0;i < chars.length;++ i) {
            cur = chars[i];
            next = i < chars.length - 1 ? chars[i + 1] : '\0';

            // Escape special characters
            if(cur == '\\' && (next == COLOR_CHAR || next == FUNCTION_CHAR || next == SECTION_START)) {
                component.append(next);
                ++ i;
                continue;
            }

            switch(cur) {
                // Update this format colors in this expression
                case COLOR_CHAR:
                {
                    // Finish off the current component if it was started
                    if(component.length() > 0) {
                        expr.add(parseComponent(format, component.toString()));
                        component.setLength(0);
                    }

                    // Get the color arguments
                    Pair<String, Integer> args = Utils.getEnclosed(i + 1, input);
                    if(args.getFirst() == null)
                        throw new SyntaxException("Bracket mismatch", i, input);
                    i = args.getSecond() - 1;

                    // Parse the color arguments
                    for(String c : args.getFirst().split(",")) {
                        boolean negated = c.startsWith("!"); // This removes formats
                        ChatColor cl = Utils.safeValueOf(ChatColor::valueOf, (negated ? c.substring(1) : c).toUpperCase());
                        if(cl == null)
                            throw new SyntaxException("Invalid color code: " + c);
                        if(cl.isFormat()) {
                            if(negated)
                                format.getSecond().remove(cl);
                            else if(!format.getSecond().contains(cl))
                                format.getSecond().add(cl);
                        }else
                            format.setFirst(cl);
                    }

                    continue;
                }

                // Start a new component
                case SECTION_START:
                {
                    // Finish off the current component if it was started
                    if(component.length() > 0) {
                        expr.add(parseComponent(format, component.toString()));
                        component.setLength(0);
                    }

                    Pair<String, Integer> section = Utils.getEnclosed(i, input);
                    if(section.getFirst() == null)
                        throw new SyntaxException("Bracket mismatch", i, input);
                    i = section.getSecond() - 1;
                    // Transfer the current formatting the the next epression in a scope-like manner
                    BaseComponent[] expression = parseExpression(new Pair<>(format.getFirst(), new ArrayList<>(format.getSecond())),
                            section.getFirst(), values);
                    expr.addAll(Arrays.asList(expression));
                    continue;
                }

                // Functions
                case FUNCTION_CHAR:
                {
                    // Get the args
                    Pair<String, Integer> rawArgs = Utils.getEnclosed(i + 1, input);
                    if(rawArgs.getFirst() == null)
                        throw new SyntaxException("Bracket mismatch", i, input);
                    List<String> args = new ArrayList<>();

                    // Build the args list
                    StringBuilder currentArg = new StringBuilder();
                    char[] cs = rawArgs.getFirst().toCharArray();
                    int depthCurly = 0, depthRound = 0;
                    for(char c : cs) {
                        if(depthCurly == 0 && depthRound == 0 && c == ',') {
                            args.add(currentArg.toString());
                            currentArg.setLength(0);
                        }else{
                            switch (c) {
                                case '(': ++ depthRound; break;
                                case '{': ++ depthCurly; break;
                                case ')': -- depthRound; break;
                                case '}': -- depthCurly; break;
                            }
                            currentArg.append(c);
                        }
                    }
                    if(depthCurly > 0 || depthRound > 0)
                        throw new SyntaxException("Bracket mismatch in function arguments", i, input);
                    i = rawArgs.getSecond() - 1;
                    args.add(currentArg.toString());

                    // Finish off the current component if it was started and we're not pluralizing a word
                    if(!"conjugate".equalsIgnoreCase(args.get(0)) && component.length() > 0) {
                        expr.add(parseComponent(format, component.toString()));
                        component.setLength(0);
                    }

                    if("link".equalsIgnoreCase(args.get(0))) { // link, text
                        if(args.size() < 3)
                            throw new SyntaxException("Link function usage: $(link,url,text)");

                        // Parse the text
                        BaseComponent[] text = parseExpression(new Pair<>(format.getFirst(), new ArrayList<>(format.getSecond())),
                                args.get(2).startsWith("{") ? Utils.getEnclosed(0, args.get(2)).getFirst() : args.get(2), values);

                        // Apply the link
                        for(BaseComponent bc : text)
                            bc.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, args.get(1)));
                        expr.addAll(Arrays.asList(text));
                    }else if("hover".equalsIgnoreCase(args.get(0))) { // hover text, base text
                        if(args.size() < 3)
                            throw new SyntaxException("Hover function usage: $(hover,hoverText,text)");

                        // Parse both texts
                        BaseComponent[] hover = parseExpression(new Pair<>(format.getFirst(), new ArrayList<>(format.getSecond())),
                                args.get(1).startsWith("{") ? Utils.getEnclosed(0, args.get(1)).getFirst() : args.get(1), values);
                        BaseComponent[] text = parseExpression(new Pair<>(format.getFirst(), new ArrayList<>(format.getSecond())),
                                args.get(2).startsWith("{") ? Utils.getEnclosed(0, args.get(2)).getFirst() : args.get(2), values);

                        // Apply the hover text
                        for(BaseComponent bc : text)
                            bc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
                        expr.addAll(Arrays.asList(text));
                    }else if("conjugate".equalsIgnoreCase(args.get(0))) { // value index, word
                        if(args.size() < 4)
                            throw new SyntaxException("Conjugate function usage: $(conjugate,noun|verb,argIndex,word)");

                        boolean noun = "noun".equalsIgnoreCase(args.get(1));

                        // Parse the value index
                        int index;
                        try {
                            index = Integer.parseInt(args.get(2));
                        }catch(NumberFormatException ex) {
                            throw new SyntaxException("Invalid index: " + args.get(2));
                        }

                        // Check the index an value
                        if(index > values.length || index < 0)
                            throw new SyntaxException("Index is out of bounds: " + index);
                        if(!(values[index] instanceof Number))
                            throw new SyntaxException("The value at the index provided is not a number.");

                        // Apply the "s" if needed
                        component.append(args.get(3));
                        int count = ((Number)values[index]).intValue();
                        if((noun && count != 1) || (!noun && count == 1))
                            component.append('s');
                    }else if("command".equalsIgnoreCase(args.get(0))) {
                        if(args.size() < 3)
                            throw new SyntaxException("Command function usage: $(command,command,text)");

                        // Parse the text
                        BaseComponent[] text = parseExpression(new Pair<>(format.getFirst(), new ArrayList<>(format.getSecond())),
                                args.get(2).startsWith("{") ? Utils.getEnclosed(0, args.get(2)).getFirst() : args.get(2), values);

                        // Apply the command
                        for(BaseComponent bc : text)
                            bc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, args.get(1)));
                        expr.addAll(Arrays.asList(text));
                    }else if("hovercmd".equalsIgnoreCase(args.get(0))) {
                        if(args.size() < 4)
                            throw new SyntaxException("Hover-command function usage: $(hovercmd,command,hoverText,text)");

                        // Parse both texts
                        BaseComponent[] hover = parseExpression(new Pair<>(format.getFirst(), new ArrayList<>(format.getSecond())),
                                args.get(2).startsWith("{") ? Utils.getEnclosed(0, args.get(2)).getFirst() : args.get(2), values);
                        BaseComponent[] text = parseExpression(new Pair<>(format.getFirst(), new ArrayList<>(format.getSecond())),
                                args.get(3).startsWith("{") ? Utils.getEnclosed(0, args.get(3)).getFirst() : args.get(3), values);

                        // Apply the command
                        for(BaseComponent bc : text) {
                            bc.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, args.get(1)));
                            bc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
                        }
                        expr.addAll(Arrays.asList(text));
                    }else if("hoverlink".equalsIgnoreCase(args.get(0))) {
                        if(args.size() < 4)
                            throw new SyntaxException("Hover-link function usage: $(hoverlink,url,hoverText,text)");

                        // Parse both texts
                        BaseComponent[] hover = parseExpression(new Pair<>(format.getFirst(), new ArrayList<>(format.getSecond())),
                                args.get(2).startsWith("{") ? Utils.getEnclosed(0, args.get(2)).getFirst() : args.get(2), values);
                        BaseComponent[] text = parseExpression(new Pair<>(format.getFirst(), new ArrayList<>(format.getSecond())),
                                args.get(3).startsWith("{") ? Utils.getEnclosed(0, args.get(3)).getFirst() : args.get(3), values);

                        // Apply the command
                        for(BaseComponent bc : text) {
                            bc.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, args.get(1)));
                            bc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
                        }
                        expr.addAll(Arrays.asList(text));
                    }else
                        throw new SyntaxException("Invalid function: " + args.get(0));
                    continue;
                }

                // Normal text
                default:
                    component.append(cur);
            }

        }

        // Get the last component
        if(component.length() > 0)
            expr.add(parseComponent(format, component.toString()));

        return expr.toArray(new BaseComponent[0]);
    }

    private static TextComponent parseComponent(Pair<ChatColor, List<ChatColor>> format, String text) {
        TextComponent tc = new TextComponent(text);
        tc.setColor(format.getFirst().asBungee());
        tc.setBold(format.getSecond().contains(ChatColor.BOLD));
        tc.setItalic(format.getSecond().contains(ChatColor.ITALIC));
        tc.setUnderlined(format.getSecond().contains(ChatColor.UNDERLINE));
        tc.setStrikethrough(format.getSecond().contains(ChatColor.STRIKETHROUGH));
        tc.setObfuscated(format.getSecond().contains(ChatColor.MAGIC));
        tc.setClickEvent(null);
        tc.setHoverEvent(null);
        return tc;
    }

    public static class SyntaxException extends RuntimeException {
        public SyntaxException(String msg, int index, String input) {
            super(msg + " near \"" + input.substring(index, Math.min(index + 8, input.length())) + "\"...");
        }

        public SyntaxException(String msg) {
            super(msg);
        }
    }
}
