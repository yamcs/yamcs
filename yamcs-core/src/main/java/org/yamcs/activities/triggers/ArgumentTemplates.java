package org.yamcs.activities.triggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${...}} placeholders in activity arguments.
 * <p>
 * A placeholder is looked up in the firing context. Placeholders of the form {@code ${param:/qualified/name}} that are
 * not in the context are resolved with a separate lookup function, typically the current engineering value of that
 * parameter.
 * <p>
 * If a string consists of a single placeholder, it is replaced by the value itself, keeping its type (useful for
 * numeric command arguments). Otherwise the placeholders are replaced by their string representation. Use
 * <code>$${</code> to write a literal <code>${</code>.
 */
public class ArgumentTemplates {

    public static final String PARAM_PREFIX = "param:";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\$\\{|\\$\\{([^}]+)\\}");

    private final Map<String, Object> context;
    private final Function<String, Object> parameterLookup;

    /**
     * @param context
     *            values by placeholder name
     * @param parameterLookup
     *            resolves the value of a parameter by qualified name, or returns null. May be null.
     */
    public ArgumentTemplates(Map<String, Object> context, Function<String, Object> parameterLookup) {
        this.context = context;
        this.parameterLookup = parameterLookup;
    }

    /**
     * Returns a copy of the arguments with all placeholders resolved.
     *
     * @throws IllegalArgumentException
     *             if a placeholder cannot be resolved
     */
    public Map<String, Object> resolve(Map<String, Object> args) {
        var result = new LinkedHashMap<String, Object>();
        for (var entry : args.entrySet()) {
            result.put(entry.getKey(), resolveObject(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object resolveObject(Object value) {
        if (value instanceof String s) {
            return resolveString(s);
        } else if (value instanceof Map<?, ?> m) {
            return resolve((Map<String, Object>) m);
        } else if (value instanceof List<?> l) {
            var result = new ArrayList<Object>(l.size());
            for (var item : l) {
                result.add(resolveObject(item));
            }
            return result;
        } else {
            return value;
        }
    }

    private Object resolveString(String s) {
        var m = PLACEHOLDER.matcher(s);
        if (m.matches() && m.group(1) != null) {
            return lookup(m.group(1).trim());
        }

        m.reset();
        var sb = new StringBuilder();
        while (m.find()) {
            String replacement;
            if (m.group(1) == null) {
                replacement = "${";
            } else {
                replacement = String.valueOf(lookup(m.group(1).trim()));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Object lookup(String key) {
        if (context.containsKey(key)) {
            return context.get(key);
        }
        if (key.startsWith(PARAM_PREFIX)) {
            var name = key.substring(PARAM_PREFIX.length()).trim();
            var value = parameterLookup != null ? parameterLookup.apply(name) : null;
            if (value == null) {
                throw new IllegalArgumentException("No value available for parameter '" + name + "'");
            }
            return value;
        }
        throw new IllegalArgumentException("Unknown placeholder '${" + key + "}'");
    }
}
