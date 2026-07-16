package org.yamcs.templating;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.Yaml;

/**
 * Processes a template source. Supported features:
 * 
 * <ul>
 * <li><code>{{ var }}</code> references are substituted with the provided args.
 * <li><code>{{ var | escape }}</code> a value is filtered through an 'escape' filter.
 * <li><code>{% if var %} ... {% else %} ... {% endif %}</code> blocks are only included if the provided var is set.
 * <li><code>{% comment %} ... {% endcomment %}</code> blocks are discarded.
 * </ul>
 */
public class TemplateProcessor {

    private TemplateBody compiledTemplate;

    private Map<String, VariableFilter> filters = new HashMap<>();

    public TemplateProcessor(String template) throws ParseException {
        try (var reader = new StringReader(template)) {
            var parser = new TemplateParser(new StringReader(template));
            compiledTemplate = parser.parse();
        }

        addFilter("escape", new EscapeFilter());
    }

    public static String process(String template, Map<String, Object> args) throws ParseException {
        return new TemplateProcessor(template).process(args);
    }

    public static String processAndSanitizeYaml(String template, Map<String, Object> args) throws ParseException {
        return new TemplateProcessor(template).processAndSanitizeYaml(args);
    }

    public void addFilter(String name, VariableFilter filter) {
        filters.put(name, filter);
    }

    public String process(Map<String, Object> args) {
        return compiledTemplate.render(args, filters);
    }

    public String processAndSanitizeYaml(Map<String, Object> args) {
        Map<String, Object> tokenArgs = new HashMap<>();
        Map<String, String> tokenToActualValueMap = new HashMap<>();

        // Swap strings out for safe, un-injectable UUID tokens
        for (var entry : args.entrySet()) {
            if (entry.getValue() instanceof String rawString) {
                String token = "___TOKEN_" + UUID.randomUUID().toString() + "___";
                tokenArgs.put(entry.getKey(), token);
                tokenToActualValueMap.put(token, rawString);
            } else {
                tokenArgs.put(entry.getKey(), entry.getValue());
            }
        }

        // Process layout logic (if, comments, filters)
        String renderedLayoutText = process(tokenArgs);

        // Parse text output into an object structure. Tokens are trapped cleanly as data strings.
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setDefaultFlowStyle(FlowStyle.BLOCK);
        Yaml yaml = new Yaml(dumperOptions);
        Object structuralRoot = yaml.load(renderedLayoutText);

        // Recursively traverse the structure to replace tokens with raw user data
        replaceTokensWithRealValues(structuralRoot, tokenToActualValueMap);

        // Serialize the safe memory graph back to text
        return yaml.dump(structuralRoot);
    }

    @SuppressWarnings("unchecked")
    private void replaceTokensWithRealValues(Object node, Map<String, String> tokenMap) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object value = entry.getValue();

                if (value instanceof String stringValue && tokenMap.containsKey(stringValue)) {
                    // Update value inside the map
                    entry.setValue(tokenMap.get(stringValue));
                } else {
                    // Continue diving into nested maps, lists, etc.
                    replaceTokensWithRealValues(value, tokenMap);
                }
            }
        } else if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);

                if (element instanceof String stringValue && tokenMap.containsKey(stringValue)) {
                    // Update element inside the array list
                    list.set(i, tokenMap.get(stringValue));
                } else {
                    // Continue diving into objects inside the list
                    replaceTokensWithRealValues(element, tokenMap);
                }
            }
        }
    }
}
