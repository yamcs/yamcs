package org.yamcs.templating;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.utils.TemplateProcessor;

public class Template {

    private String name;
    private String source;
    private String description;
    private Map<String, Variable<?>> variables = new HashMap<>();

    public Template(String name, String source) {
        this.name = name;
        this.source = source;
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

    public Collection<Variable<?>> getVariables() {
        return variables.values();
    }

    public void addVariable(Variable<?> variable) {
        variables.put(variable.getName(), variable);
    }

    public String process(Map<String, Object> args) {
        return TemplateProcessor.process(source, args);
    }

    @Override
    public String toString() {
        return name;
    }
}
