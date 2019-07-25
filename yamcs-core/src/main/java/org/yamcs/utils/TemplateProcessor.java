package org.yamcs.utils;

import java.util.Collection;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Processes a template source. Supported features:
 * 
 * <ul>
 * <li><code>{{ var }}</code> references are substituted with the provided args.
 * <li><code>{% if var %} ... {% endif %}</code> blocks are only included if the provided var is set.
 * <li><code>{% comment %} ... {% endcomment %}</code> blocks are discarded.
 * </ul>
 */
public class TemplateProcessor {

    private static final Pattern VAR_BEGIN = Pattern.compile("\\{\\{");
    private static final Pattern VAR_CONTINUE = Pattern.compile("\\s?([\\w_\\.]+)\\s?\\}\\}");

    private static final Pattern TAG_BEGIN = Pattern.compile("\\{\\%");
    private static final Pattern TAG_CONTINUE = Pattern.compile("\\s?([\\w]+)\\s+([^\\{\\}]*)\\%\\}");

    private String template;

    private int endifExpected = 0;

    public TemplateProcessor(String template) {
        this.template = template;
    }

    public static String process(String template, Map<String, Object> args) {
        return new TemplateProcessor(template).process(args);
    }

    public String process(Map<String, Object> args) {
        StringBuffer buf = new StringBuffer();
        try (Scanner scanner = new Scanner(template)) {
            scanner.useDelimiter("\\{");
            while (scanner.hasNext()) {
                if (scanner.findWithinHorizon(VAR_BEGIN, 2) != null) {
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
                    switch (tagName) {
                    case "comment":
                        processComment(scanner);
                        break;
                    case "if":
                        String condition = scanner.match().group(2).trim();
                        processIf(scanner, condition, args);
                        break;
                    case "endif":
                        processEndif(scanner);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported tag '" + tagName + "'");
                    }
                } else {
                    String next = scanner.next();
                    buf.append(next);
                }
            }
        }
        return buf.toString();
    }

    private void processIf(Scanner scanner, String condition, Map<String, Object> args) {
        endifExpected++;

        Object variableValue = getValue(condition, args);

        // Skip until matching {% endif %}
        // Any skipped content is otherwise unprocessed.
        if (isFalsy(variableValue)) {
            int returnAt = endifExpected - 1;
            while (scanner.hasNext()) {
                if (scanner.findWithinHorizon(TAG_BEGIN, 2) != null) {
                    if (scanner.findInLine(TAG_CONTINUE) != null) {
                        String tagName = scanner.match().group(1);
                        if (tagName.equals("if")) {
                            endifExpected++;
                        } else if (tagName.equals("endif")) {
                            processEndif(scanner);
                            if (endifExpected == returnAt) {
                                return;
                            }
                        }
                    }
                } else {
                    scanner.next();
                }
            }

            throw new IllegalStateException("Unclosed if tag");
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
        if (endifExpected > 0) {
            endifExpected--;
        } else {
            throw new IllegalStateException("Unmatched endif tag");
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
}
