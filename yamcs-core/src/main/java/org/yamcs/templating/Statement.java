package org.yamcs.templating;

import java.util.Map;

public interface Statement {

    void render(StringBuilder buf, Map<String, Object> vars, Map<String, VariableFilter> filters);
}
