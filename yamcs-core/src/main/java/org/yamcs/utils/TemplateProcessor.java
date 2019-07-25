package org.yamcs.utils;

import java.util.Collection;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;
import java.util.regex.Pattern;

/**
 * Processes a template source. Supported features:
 * 
 * <ul>
 * <li><code>{{ var }}</code> references are substituted with the provided args.
 * <li><code>{% if var %} ... {% else %} ... {% endif %}</code> blocks are only included if the provided var is set.
 * <li><code>{% comment %} ... {% endcomment %}</code> blocks are discarded.
 * </ul>
 */
public class TemplateProcessor {

    private static final Pattern VAR_BEGIN = Pattern.compile("\\{\\{");
    private static final Pattern VAR_CONTINUE = Pattern.compile("\\s?([\\w_\\.]+)\\s?\\}\\}");

    private static final Pattern TAG_BEGIN = Pattern.compile("\\{\\%");
    private static final Pattern TAG_CONTINUE = Pattern.compile("\\s?([\\w]+)\\s+([^\\{\\}]*)\\%\\}");

    private String template;

    private String lookahead;
    private Stack<PendingCondition> pendingConditions = new Stack<>();

    public TemplateProcessor(String template) {
        this.template = template;
    }

    public static String process(String template, Map<String, Object> args) {
        return new TemplateProcessor(template).process(args);
    }

    public String process(Map<String, Object> args) {
        StringBuilder buf = new StringBuilder();
        try (Scanner scanner = new Scanner(template)) {
            scanner.useDelimiter("\\{");
            while (lookahead != null || scanner.hasNext()) {
                if (lookahead != null) {
                    String tagName = lookahead;
                    lookahead = null;
                    processTag(scanner, tagName, args);
                } else if (scanner.findWithinHorizon(VAR_BEGIN, 2) != null) {
                    if (scanner.findInLine(VAR_CONTINUE) == null) {
                        throw new IllegalStateException("Unclosed variable. Expected }}");
                    }

                    String variableName = scanner.match().group(1).trim();
                    Object variableValue = getValue(variableName, args);
                    if (variableValue == null) {
                        throw new IllegalArgumentException(String.format("Variable '%s' is not set", variableName));
                    }
                    buf.append(variableValue);
                } else if (scanner.findWithinHorizon(TAG_BEGIN, 2) != null) {
                    if (scanner.findInLine(TAG_CONTINUE) == null) {
                        throw new IllegalStateException("Unclosed block. Expected %}");
                    }

                    String tagName = scanner.match().group(1);
                    processTag(scanner, tagName, args);
                } else {
                    String next = scanner.next();
                    buf.append(next);
                }
            }
        }
        return buf.toString();
    }

    private void processTag(Scanner scanner, String tagName, Map<String, Object> args) {
        switch (tagName) {
        case "comment":
            processComment(scanner);
            break;
        case "if":
            String ifCondition = scanner.match().group(2).trim();
            processIf(scanner, ifCondition, args);
            break;
        case "elif":
            String elifCondition = scanner.match().group(2).trim();
            processElif(scanner, elifCondition, args);
            break;
        case "else":
            processElse(scanner, args);
            break;
        case "endif":
            processEndif(scanner);
            break;
        default:
            throw new IllegalStateException("Unsupported tag '" + tagName + "'");
        }
    }

    private void processIf(Scanner scanner, String condition, Map<String, Object> args) {

        Object variableValue = getValue(condition, args);
        boolean resolved = isTruthy(variableValue);
        pendingConditions.add(new PendingCondition(resolved));

        // Skip this clause
        // Any skipped content is otherwise unprocessed.
        if (!resolved) {
            int ignoredEndif = 1;
            while (scanner.hasNext()) {
                if (scanner.findWithinHorizon(TAG_BEGIN, 2) != null) {
                    if (scanner.findInLine(TAG_CONTINUE) != null) {
                        String tagName = scanner.match().group(1);
                        switch (tagName) {
                        case "if":
                            ignoredEndif++;
                            break;
                        case "elif":
                            if (ignoredEndif == 1) {
                                lookahead = "elif";
                                return;
                            }
                            break;
                        case "else":
                            if (ignoredEndif == 1) {
                                return;
                            }
                            break;
                        case "endif":
                            if (ignoredEndif > 0) {
                                ignoredEndif--;
                            } else {
                                throw new IllegalStateException("Unmatched endif tag");
                            }
                            if (ignoredEndif == 0) {
                                return;
                            }
                            break;
                        }
                    }
                } else {
                    scanner.next();
                }
            }

            throw new IllegalStateException("Unclosed if tag");
        }
    }

    private void processElif(Scanner scanner, String condition, Map<String, Object> args) {
        if (pendingConditions.isEmpty()) {
            throw new IllegalStateException("Tag 'elif' must be preceded by a matching 'if'");
        }

        if (!pendingConditions.peek().resolved) {
            Object variableValue = getValue(condition, args);
            if (isTruthy(variableValue)) {
                pendingConditions.peek().resolved = true;
                return;
            }
        }

        // Skip this clause.
        // Any skipped content is otherwise unprocessed.
        int ignoredEndif = 1;
        while (scanner.hasNext()) {
            if (scanner.findWithinHorizon(TAG_BEGIN, 2) != null) {
                if (scanner.findInLine(TAG_CONTINUE) != null) {
                    String tagName = scanner.match().group(1);
                    switch (tagName) {
                    case "if":
                        ignoredEndif++;
                        break;
                    case "elif":
                        if (ignoredEndif == 1) {
                            lookahead = "elif";
                            return;
                        }
                        break;
                    case "else":
                        if (ignoredEndif == 1) {
                            lookahead = "else";
                            return;
                        }
                        break;
                    case "endif":
                        if (ignoredEndif > 0) {
                            ignoredEndif--;
                        } else {
                            throw new IllegalStateException("Unmatched endif tag");
                        }
                        if (ignoredEndif == 0) {
                            return;
                        }
                        break;
                    }
                }
            } else {
                scanner.next();
            }
        }

        throw new IllegalStateException("Unclosed if/else tag");
    }

    private void processElse(Scanner scanner, Map<String, Object> args) {
        if (pendingConditions.isEmpty()) {
            throw new IllegalStateException("Tag 'else' must be preceded by a matching 'if'");
        }

        // Skip this clause
        // Any skipped content is otherwise unprocessed.
        if (pendingConditions.peek().resolved) {
            int ignoredEndif = 1;
            while (scanner.hasNext()) {
                if (scanner.findWithinHorizon(TAG_BEGIN, 2) != null) {
                    if (scanner.findInLine(TAG_CONTINUE) != null) {
                        String tagName = scanner.match().group(1);
                        if (tagName.equals("if")) {
                            ignoredEndif++;
                        } else if (tagName.equals("endif")) {
                            if (ignoredEndif > 0) {
                                ignoredEndif--;
                            } else {
                                throw new IllegalStateException("Unmatched endif tag");
                            }
                            if (ignoredEndif == 0) {
                                return;
                            }
                        }
                    }
                } else {
                    scanner.next();
                }
            }

            throw new IllegalStateException("Unclosed if/else tag");
        }
    }

    private Object getValue(String name, Map<String, Object> args) {
        return getValue(name, args, "");
    }

    @SuppressWarnings("unchecked")
    private Object getValue(String name, Map<String, Object> args, String nameContext) {
        int dotIndex = name.indexOf('.');
        if (dotIndex == -1) {
            return args.get(name);
        } else {
            String parentName = name.substring(0, dotIndex);
            Object parentValue = args.get(parentName);
            if (parentValue == null) {
                throw new IllegalArgumentException(String.format(
                        "Variable '%s%s' is not set", nameContext, parentName));
            }
            if (!(parentValue instanceof Map)) {
                throw new IllegalArgumentException(String.format(
                        "Variable '%s%s' is not a map", nameContext, parentName));
            }
            Map<String, Object> parentArgs = (Map<String, Object>) parentValue;
            return getValue(name.substring(dotIndex + 1), parentArgs);
        }
    }

    private boolean isTruthy(Object value) {
        return !isFalsy(value);
    }

    private boolean isFalsy(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        if (value.getClass().isArray()) {
            return ((Object[]) value).length == 0;
        }
        return false;
    }

    private void processEndif(Scanner scanner) {
        if (pendingConditions.isEmpty()) {
            throw new IllegalStateException("Unmatched endif tag");
        } else {
            pendingConditions.pop();
        }
    }

    private void processComment(Scanner scanner) {
        while (scanner.hasNext()) {
            if (scanner.findWithinHorizon(TAG_BEGIN, 2) != null) {
                if (scanner.findInLine(TAG_CONTINUE) != null) {
                    String tagName = scanner.match().group(1);
                    if (tagName.equals("endcomment")) {
                        return;
                    }
                }
            } else {
                scanner.next();
            }
        }

        throw new IllegalStateException("Unclosed comment tag");
    }

    private static final class PendingCondition {

        boolean resolved;

        PendingCondition(boolean resolved) {
            this.resolved = resolved;
        }
    }
}
