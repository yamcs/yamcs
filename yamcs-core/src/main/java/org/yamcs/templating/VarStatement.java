package org.yamcs.templating;

import java.util.List;
import java.util.Map;

public class VarStatement implements Statement {

    private String identifier;
    private List<String> filterIdentifiers;

    public VarStatement(String identifier, List<String> filterIdentifiers) {
        this.identifier = identifier;
        this.filterIdentifiers = filterIdentifiers;
    }

    @Override
    public void render(StringBuilder buf, Map<String, Object> vars, Map<String, VariableFilter> filters) {
        var value = getValue(identifier, vars);
        for (var filterIdentifier : filterIdentifiers) {
            var filter = filters.get(filterIdentifier);
            if (filter == null) {
                throw new IllegalArgumentException(String.format("Unknown filter '%s'", filterIdentifier));
            }
            value = filter.applyFilter(value);
        }
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
