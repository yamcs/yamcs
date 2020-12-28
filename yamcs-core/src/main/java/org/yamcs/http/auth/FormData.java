package org.yamcs.http.auth;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.http.HandlerContext;

public abstract class FormData {

    protected HandlerContext ctx;
    protected Map<String, String> parameters = new HashMap<>();

    public FormData(HandlerContext ctx) {
        this.ctx = ctx;
    }

    protected void requireParameters(String... parameters) {
        for (String parameter : parameters) {
            requireParameter(parameter);
        }
    }

    protected void requireParameter(String parameter) {
        if (ctx.isFormEncoded()) {
            parameters.put(parameter, ctx.requireFormParameter(parameter));
        } else {
            parameters.put(parameter, ctx.requireQueryParameter(parameter));
        }
    }

    protected void acceptParameters(String... parameters) {
        for (String parameter : parameters) {
            acceptParameter(parameter);
        }
    }

    protected void acceptParameter(String parameter) {
        String value;
        if (ctx.isFormEncoded()) {
            value = ctx.getFormParameter(parameter);
        } else {
            value = ctx.getQueryParameter(parameter);
        }
        if (value != null) {
            parameters.put(parameter, value);
        }
    }

    public Map<String, String> getMap() {
        return parameters;
    }
}
