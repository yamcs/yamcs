package org.yamcs.utils;

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
    private static final Pattern VAR_CONTINUE = Pattern.compile("\\s?([\\w_]+)\\s?\\}\\}");

    private static final Pattern TAG_BEGIN = Pattern.compile("\\{\\%");
    private static final Pattern TAG_CONTINUE = Pattern.compile("\\s?([\\w]+)\\s+([^\\{\\}]*)\\%\\}");

    private String template;

    private int endifExpected = 0;

    public TemplateProcessor(String template) {
        this.template = template;
    }

    public static String process(String template, Map<String, String> args) {
        return new TemplateProcessor(template).process(args);
    }

    public String process(Map<String, String> args) {
        StringBuffer buf = new StringBuffer();
        try (Scanner scanner = new Scanner(template)) {
            scanner.useDelimiter("\\{");
            while (scanner.hasNext()) {
                if (scanner.findWithinHorizon(VAR_BEGIN, 2) != null) {
                    if (scanner.findInLine(VAR_CONTINUE) == null) {
                        throw new IllegalStateException("Unclosed variable. Expected }}");
                    }

                    String variableName = scanner.match().group(1).trim();
                    String variableValue = args.get(variableName);
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

    private void processIf(Scanner scanner, String condition, Map<String, String> args) {
        endifExpected++;

        String variableValue = args.get(condition);

        // Skip until matching {% endif %}
        // Any skipped content is otherwise unprocessed.
        if (variableValue == null || variableValue.trim().isEmpty()) {
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
