package org.yamcs.utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateUtil {

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s?([\\w_]+)\\s?\\}\\}");

    /**
     * Processes a template source whereby <code>{{ var }}</code> references are substituded with the provided args.
     */
    public static String process(String source, Map<String, String> args) {
        StringBuffer buf = new StringBuffer();
        Matcher m = VAR.matcher(source);
        while (m.find()) {
            String variableName = m.group(1);
            String variableValue = args.get(variableName);
            if (variableValue == null) {
                throw new IllegalArgumentException(
                        String.format("Variable %s is not specified", variableName));
            }
            m.appendReplacement(buf, variableValue);
        }
        m.appendTail(buf);
        return buf.toString();
    }
}
