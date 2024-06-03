package org.yamcs.actions;

import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

public class ActionResult {

    private CompletableFuture<JsonObject> future = new CompletableFuture<>();

    /**
     * Complete successfully, without response message.
     * <p>
     * Shortcut for {@code complete(null)}
     */
    public void complete() {
        complete(null);
    }

    /**
     * Complete successfully
     *
     * @param result
     *            the result value (may be null)
     */
    public void complete(JsonObject result) {
        future.complete(result);
    }

    /**
     * Complete with an exception
     */
    public void completeExceptionally(Throwable t) {
        future.completeExceptionally(t);
    }

    /**
     * Return the underlying future.
     */
    public CompletableFuture<JsonObject> future() {
        return future;
    }
}
