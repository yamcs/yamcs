package org.yamcs.templating;

/**
 * Escapes HTML in a string.
 */
public class EscapeFilter implements VariableFilter {

    @Override
    public Object applyFilter(Object variable) {
        if (variable instanceof String) {
            return ((String) variable)
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("'", "&#x27;")
                    .replace("\"", "&quot;");
        }

        return variable;
    }
}
