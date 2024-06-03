package org.yamcs.tctm;

import org.yamcs.actions.Action;
import org.yamcs.actions.ActionResult;

import com.google.gson.JsonObject;

public abstract class LinkAction extends Action<Link> {

    public LinkAction(String id, String label) {
        super(id, label);
    }

    public LinkAction(String id, String label, ActionStyle style) {
        super(id, label, style);
    }

    @Override
    public void execute(Link target, JsonObject request, ActionResult result) {
        var responseMessage = execute(target, request);
        result.complete(responseMessage);
    }

    /**
     * @deprecated Implement {@link #execute(Link, JsonObject, ActionResult)} instead
     */
    @Deprecated
    public JsonObject execute(Link target, JsonObject request) {
        return null;
    }
}
