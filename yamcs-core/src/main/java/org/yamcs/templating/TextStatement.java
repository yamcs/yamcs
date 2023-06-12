package org.yamcs.templating;

import java.util.Map;

public class TextStatement implements Statement {

    private String text;

    public TextStatement(String text) {
        this.text = text;
    }

    @Override
    public void render(StringBuilder buf, Map<String, Object> vars) {
        buf.append(text);
    }
}
