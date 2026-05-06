package org.yamcs.activities;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;

/**
 * Discovery interface for creating runners.
 * <p>
 * Note there may be multiple runners of the same type, which is why this is modeled as a factory.
 */
public interface ScriptRunnerFactory {

    /**
     * The type, as used in config
     */
    String getType();

    /**
     * Specify the available options for runners of this type
     */
    Spec getSpec();

    /**
     * Create a new script runner, for the specific set of provided options.
     */
    ScriptRunner createScriptRunner(String name, YConfiguration options);
}
