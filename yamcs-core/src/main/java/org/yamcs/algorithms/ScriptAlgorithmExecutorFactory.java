package org.yamcs.algorithms;

import static org.yamcs.algorithms.AlgorithmManager.JDK_BUILTIN_NASHORN_ENGINE_NAME;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.OutputParameter;

/**
 * Handles the creation of algorithm executors for script algorithms for a given language and scriptEngine (currently
 * javascript or python are supported).
 * <p>
 * Each algorithm is created as a function in the scriptEngine. There might be multiple executors for the same
 * algorithm: for example in the command verifier there will be one algorithm executor for each command. However there
 * will be only one function created in the script engine.
 *
 * 
 */
public class ScriptAlgorithmExecutorFactory implements AlgorithmExecutorFactory {
    final ScriptEngine scriptEngine;
    static final Logger log = LoggerFactory.getLogger(ScriptAlgorithmExecutorFactory.class);

    public ScriptAlgorithmExecutorFactory(ScriptEngineManager scriptEngineManager, String language,
            List<String> libraryNames) {

        // Custom lookup instead of ScriptEngineManager.getEngineByName because we want
        // to include the JDK11-14 builtin Nashorn in favour of Nashorn from the classpath.
        ScriptEngineFactory factory = scriptEngineManager.getEngineFactories().stream()
                .filter(candidate -> !JDK_BUILTIN_NASHORN_ENGINE_NAME.equals(candidate.getEngineName())
                        && candidate.getNames().contains(language))
                .findFirst()
                .orElse(null);

        if (factory != null) {
            scriptEngine = factory.getScriptEngine();
            scriptEngine.setBindings(scriptEngineManager.getBindings(), ScriptContext.GLOBAL_SCOPE);
        } else {
            throw new ConfigurationException("Cannot get a script engine for language " + language);
        }

        if (libraryNames != null) {
            loadLibraries(libraryNames);
        }

        // Put engine bindings in shared global scope - we want the variables in the libraries to be global
        Bindings commonBindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
        Set<String> existingBindings = new HashSet<>(scriptEngineManager.getBindings().keySet());

        existingBindings.retainAll(commonBindings.keySet());
        if (!existingBindings.isEmpty()) {
            throw new ConfigurationException(
                    "Overlapping definitions found while loading libraries for language " + language + ": "
                            + existingBindings);
        }
        commonBindings.putAll(scriptEngineManager.getBindings());
        scriptEngineManager.setBindings(commonBindings);
    }

    private void loadLibraries(List<String> libraryNames) {
        try {
            for (String lib : libraryNames) {
                log.debug("Loading library {}", lib);
                File f = new File(lib);
                if (!f.exists()) {
                    throw new ConfigurationException("Algorithm library file '" + f + "' does not exist");
                }
                scriptEngine.put(ScriptEngine.FILENAME, f.getPath()); // Improves error msgs
                if (f.isFile()) {
                    try (FileReader fr = new FileReader(f)) {
                        scriptEngine.eval(fr);
                    }
                } else {
                    throw new ConfigurationException("Specified library is not a file: " + f);
                }
            }
        } catch (IOException e) { // Force exit. User should fix this before continuing
            throw new ConfigurationException("Cannot read from library file", e);
        } catch (ScriptException e) { // Force exit. User should fix this before continuing
            throw new ConfigurationException("Script error found in library file: " + e.getMessage(), e);
        }
    }

    @Override
    public ScriptAlgorithmExecutor makeExecutor(CustomAlgorithm calg, AlgorithmExecutionContext execCtx) {
        String functionName = calg.getQualifiedName().replace("/", "_");
        String functionScript = generateFunctionCode(functionName, calg);
        log.debug("Evaluating script:\n{}", functionScript);
        try {
            // improve error messages as well as required for event generation to know from where it is called
            scriptEngine.put(ScriptEngine.FILENAME, calg.getQualifiedName());
            scriptEngine.eval(functionScript);
        } catch (ScriptException e) {
            String msg = "Error evaluating script " + functionScript + ": " + e.getMessage();
            execCtx.getEventProducer().sendWarning(msg);
            log.warn("Error while evaluating script {}: {}", functionScript, e.getMessage(), e);
            throw new AlgorithmException(msg);
        }
        return new ScriptAlgorithmExecutor(calg, (Invocable) scriptEngine, functionName, functionScript, execCtx);
    }

    public static String generateFunctionCode(String functionName, CustomAlgorithm algorithmDef) {
        StringBuilder sb = new StringBuilder();

        String language = algorithmDef.getLanguage();
        if ("JavaScript".equalsIgnoreCase(language)) {
            sb.append("function ").append(functionName);
        } else if ("python".equalsIgnoreCase(language)) {
            sb.append("def ").append(functionName);
        } else {
            throw new IllegalArgumentException("Cannot execute scripts in " + language);
        }
        sb.append("(");

        boolean firstParam = true;
        for (InputParameter inputParameter : algorithmDef.getInputList()) {
            // Default-define all input values to null to prevent ugly runtime errors
            String argName = inputParameter.getEffectiveInputName();
            if (firstParam) {
                firstParam = false;
            } else {
                sb.append(", ");
            }
            sb.append(argName);
        }

        // Set empty output bindings so that algorithms can write their attributes
        for (OutputParameter outputParameter : algorithmDef.getOutputList()) {
            String scriptName = outputParameter.getOutputName();
            if (scriptName == null) {
                scriptName = outputParameter.getParameter().getName();
            }
            if (firstParam) {
                firstParam = false;
            } else {
                sb.append(", ");
            }
            sb.append(scriptName);
        }
        sb.append(")");

        if ("JavaScript".equalsIgnoreCase(language)) {
            sb.append(" {\n");
        } else if ("python".equalsIgnoreCase(language)) {
            sb.append(":\n");
        }

        String[] a = algorithmDef.getAlgorithmText().split("\\r?\\n");
        for (String l : a) {
            sb.append("    ").append(l).append("\n");
        }

        if ("JavaScript".equalsIgnoreCase(language)) {
            sb.append("}");
        }
        return sb.toString();
    }

    @Override
    public List<String> getLanguages() {
        return scriptEngine.getFactory().getNames();
    }
}
