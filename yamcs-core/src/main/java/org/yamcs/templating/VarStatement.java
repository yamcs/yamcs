package org.yamcs.templating;

import java.util.Map;

public class VarStatement implements Statement {

    private String identifier;

    public VarStatement(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public void render(StringBuilder buf, Map<String, Object> vars) {
        var value = getValue(identifier, vars);
        if (value == null) {
            throw new IllegalArgumentException(String.format("Variable '%s' is not set", identifier));
        }
        buf.append(value);
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
}
