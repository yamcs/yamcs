package org.yamcs.templating;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.yaml.snakeyaml.Yaml;

public class Template {

    private String name;
    private String source;
    private String description;
    private Map<String, Variable> variables = new LinkedHashMap<>(); // Ordered

    private TemplateProcessor templateProcessor;

    public Template(String name, String source) throws ParseException {
        this.name = name;
        this.source = source;
        templateProcessor = new TemplateProcessor(source);
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Collection<Variable> getVariables() {
        return variables.values();
    }

    public void addVariable(Variable variable) {
        variables.put(variable.getName(), variable);
    }

    public String process(Map<String, Object> args) {
        return templateProcessor.process(args);
    }

    public String processAndSanitizeYaml(Map<String, Object> userArgs) {
        Map<String, Object> tokenArgs = new HashMap<>();
        Map<String, String> tokenToActualValueMap = new HashMap<>();

        // 1. Swap user strings out for safe, un-injectable UUID tokens
        for (var entry : userArgs.entrySet()) {
            if (entry.getValue() instanceof String rawString) {
                String token = "___TOKEN_" + UUID.randomUUID().toString() + "___";
                tokenArgs.put(entry.getKey(), token);
                tokenToActualValueMap.put(token, rawString);
            } else {
                tokenArgs.put(entry.getKey(), entry.getValue());
            }
        }

        // 2. Process layout logic (if, comments, filters) via your custom text engine
        String renderedLayoutText = this.templateProcessor.process(tokenArgs);

        // 3. Parse text output into an object structure. Tokens are trapped cleanly as data strings.
        Yaml yaml = new Yaml();
        Object structuralRoot = yaml.load(renderedLayoutText);

        // 4. Recursively traverse the structure to replace tokens with raw user data
        replaceTokensWithRealValues(structuralRoot, tokenToActualValueMap);

        // 5. Serialize the safe memory graph back to text
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

    @Override
    public String toString() {
        return name;
    }
}
