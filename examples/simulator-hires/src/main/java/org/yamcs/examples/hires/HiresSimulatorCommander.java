package org.yamcs.examples.hires;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.InitException;
import org.yamcs.ProcessRunner;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;

/**
 * YAMCS service that launches the {@link HiresSimulator} as an external process.
 * Follows the same pattern as {@code SimulatorCommander}.
 *
 * <p>
 * Configuration example in yamcs.hires.yaml:
 * <pre>
 *   - class: org.yamcs.examples.hires.HiresSimulatorCommander
 *     args:
 *       tmPort: 10015
 * </pre>
 */
public class HiresSimulatorCommander extends ProcessRunner {

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("tmPort", OptionType.INTEGER).withDefault(10015);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        int tmPort = config.getInt("tmPort", 10015);

        List<String> cmdl = new ArrayList<>();
        cmdl.add(new File(System.getProperty("java.home"), "bin/java").toString());
        cmdl.add(HiresSimulator.class.getName());
        cmdl.add("--port");
        cmdl.add(Integer.toString(tmPort));

        try {
            Map<String, Object> processRunnerConfig = new HashMap<>();
            processRunnerConfig.put("command", cmdl);
            processRunnerConfig.put("logPrefix", "");
            Map<String, Object> processEnvironment = new HashMap<>();
            processEnvironment.put("CLASSPATH", System.getProperty("java.class.path"));
            processRunnerConfig.put("environment", processEnvironment);
            processRunnerConfig = super.getSpec().validate(processRunnerConfig);
            super.init(yamcsInstance, serviceName, YConfiguration.wrap(processRunnerConfig));
        } catch (ValidationException e) {
            throw new InitException(e.getMessage());
        }
    }
}
