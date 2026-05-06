package org.yamcs.activities;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Runs the script of a script activity.
 */
public interface ScriptRunner {

    String getName();

    CompletableFuture<List<String>> getScripts();

    ScriptRun createRun(String script, List<String> scriptArgs);
}
