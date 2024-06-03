package org.yamcs.templating;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class IfStatement implements Statement {

    private String identifier;
    private TemplateBody ifBody;
    private LinkedHashMap<String, TemplateBody> elifConditions;
    private TemplateBody elseBody;

    public IfStatement(String identifier, TemplateBody ifBody, LinkedHashMap<String, TemplateBody> elifConditions,
            TemplateBody elseBody) {
        this.identifier = identifier;
        this.ifBody = ifBody;
        this.elifConditions = elifConditions;
        this.elseBody = elseBody;
    }

    @Override
    public void render(StringBuilder buf, Map<String, Object> vars, Map<String, VariableFilter> filters) {
        var value = getValue(identifier, vars);
        if (isTruthy(value)) {
            buf.append(ifBody.render(vars, filters));
            return;
        }
        for (var entry : elifConditions.entrySet()) {
            value = getValue(entry.getKey(), vars);
            if (isTruthy(value)) {
                buf.append(entry.getValue().render(vars, filters));
                return;
            }
        }
        if (elseBody != null) {
            buf.append(elseBody.render(vars, filters));
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
}
