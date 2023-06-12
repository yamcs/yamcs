package org.yamcs.templating;

import java.io.StringReader;
import java.util.Map;

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

    private TemplateBody compiledTemplate;

    public TemplateProcessor(String template) throws ParseException {
        try (var reader = new StringReader(template)) {
            var parser = new TemplateParser(new StringReader(template));
            compiledTemplate = parser.parse();
        }
    }

    public static String process(String template, Map<String, Object> args) throws ParseException {
        return new TemplateProcessor(template).process(args);
    }

    public String process(Map<String, Object> args) {
        return compiledTemplate.render(args);
    }
}
