package org.yamcs.xtceproc;

import java.util.List;
import java.util.Map;

import org.yamcs.commanding.ArgumentValue;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.DataSource;
import org.yamcs.xtce.Parameter;

/**
 * This class holds live information used during a (XTCE) processing.
 * <p>
 * It is created when data is incoming and is used in a processing pipeline. For example when a packet is
 * received, the following pipeline is executed:
 * <ul>
 * <li>a new processing data object is instantiated containing an empty parameter list and a reference to the latest
 * parameter value cache</li>
 * <li>parameters are added to it as extracted from the packet</li>
 * <li>calibration (raw to engineering conversion) is performed on the newly added parameters</li>
 * <li>algorithms are run creating new parameters also added to the processing data object</li>
 * <li>monitoring and generation of alarms is done for the new parameters</li>
 * <li>command verifiers are run (possibly creating new parameters TBD)</li>
 * <li>the parameter archive is populated</li>
 * </ul>
 * <p>
 * A processing can also start when receiving command history information (used as part of
 * command verification), when receiving processed parameters via the pp stream or periodically by algorithms.
 * <p>
 * There will be different threads running in parallel, each with its own ProcessingData object but one object cannot be
 * shared with another thread.
 * 
 * <p>
 * The following data is part of this object:
 * <ul>
 * <li>tmParamsCache - stores the latest values of the "released" TM parameters from a processor (processor's
 * lastValueCache). Data can be updated from outside of this processing thread. The cache will buffer older values for
 * some parameters if required (for example algorithms or match criteria can require old instances of parameter
 * values).</li>
 * <li>tmParams - stores the "current" delivery - parameters that are being produced during the current pipeline
 * execution. This list will only modify from within the pipeline (and is not visible outside) and all the data here is
 * considered "more recent" than the data from the tmParamsCache. At the end of the execution of the pipeline, the data
 * is moved from here to the tmParamsCache (i.e the processor lastValueCache)</li>
 * <li>cmdArgs - if the processing is part of a command, this stores the arguments of the command. Otherwise it is
 * null. The command arguments are not modified during the pipeline execution.</li>
 * <li>cmdParamsCache - this is similar with the tmParams but stores parameters that are contextualized to the
 * command. This includes command properties and command history parameters. The list can modify from outside the
 * pipeline.</li>
 * <li>cmdParams - stores the "current" delivery command parameters. It is initialised when command history
 * information is received. New elements can be added by algorithms producing command parameter values. A command
 * parameter is one whose datasource is {@link DataSource#COMMAND}.
 * </ul>
 * 
 * 
 */
public class ProcessingData {
    final protected LastValueCache tmParamsCache;
    final protected ParameterValueList tmParams;
    final protected Map<Argument, ArgumentValue> cmdArgs;
    final protected LastValueCache cmdParamsCache;
    final protected ParameterValueList cmdParams;

    /**
     * Used in a TM processing pipeline - for example when a TM packet is received
     */
    public static ProcessingData createForTmProcessing(LastValueCache tmValueCache) {
        return new ProcessingData(tmValueCache, new ParameterValueList(), null, null, null);
    }

    /**
     * Used in TC processing when command history events are received, they will be added to the cmdParams.
     */
    public static ProcessingData createForCmdProcessing(LastValueCache tmValueCache,
            Map<Argument, ArgumentValue> arguments, LastValueCache cmdLastValueCache) {
        return new ProcessingData(tmValueCache, new ParameterValueList(), arguments, cmdLastValueCache,
                new ParameterValueList());
    }

    /**
     * Processing data which contains values to be used in algorithm initialisation.
     * <p>
     * The tmParams and cmdParams will be null.
     */
    public static ProcessingData createInitial(LastValueCache tmParamsCache, Map<Argument, ArgumentValue> arguments,
            LastValueCache cmdParamsCache) {
        return new ProcessingData(tmParamsCache, null, arguments, cmdParamsCache, null);
    }

    /**
     * Create a new processing data object with the tmParamsCache and tmParams shared with the data object, but with new
     * cmdParams. To be used in command verifiers - each command has its own context with different cmdParams.
     * <p>
     * It is used when starting a command processing chain.
     */
    public static ProcessingData cloneForCommanding(ProcessingData data, Map<Argument, ArgumentValue> arguments,
            LastValueCache cmdParams) {
        return new ProcessingData(data.tmParamsCache, data.tmParams, arguments, cmdParams, new ParameterValueList());
    }

    /**
     * keeps the tmParamsCache and tmParams from the given data
     */
    public static ProcessingData cloneForTm(ProcessingData data) {
        return new ProcessingData(data.tmParamsCache, data.tmParams, null, null, null);
    }

    /**
     * creates an object with an empty cache and with the given values as current tm delivery. Used in unit tests
     */
    public static ProcessingData createForTestTm(ParameterValue... pvlist) {
        ProcessingData data = new ProcessingData(new LastValueCache(), new ParameterValueList(), null, null, null);
        for (ParameterValue pv : pvlist) {
            data.addTmParam(pv);
        }
        return data;
    }

    /**
     * same as above but creates command parameters
     */
    public static ProcessingData createForTestCmd(ParameterValue... pvlist) {
        ProcessingData data = new ProcessingData(new LastValueCache(), null,
                null, new LastValueCache(), new ParameterValueList());
        for (ParameterValue pv : pvlist) {
            data.addCmdParam(pv);
        }
        return data;
    }

    public ProcessingData(LastValueCache lastValueCache, ParameterValueList tmParams,
            Map<Argument, ArgumentValue> cmdArgs, LastValueCache cmdParamsCache, ParameterValueList cmdParams) {
        this.tmParams = tmParams;
        this.tmParamsCache = lastValueCache;
        this.cmdArgs = cmdArgs;
        this.cmdParams = cmdParams;
        this.cmdParamsCache = cmdParamsCache;
    }

    public ParameterValueList getTmParams() {
        return tmParams;
    }

    public void addTmParam(ParameterValue pv) {
        tmParams.add(pv);
    }

    public void addTmParams(List<ParameterValue> params) {
        tmParams.addAll(params);
    }

    public void addCmdParam(ParameterValue pv) {
        cmdParams.add(pv);
    }

    /**
     * Returns a parameter value associated to the parameter {@code param} or null if none is found.
     * <p>
     * The instance is according to XTCE rules: if tmParams contains multiple values for the parameter {@code param},
     * then the oldest one (first inserted in the list) is instance 0, the next one is instance 1, etc.
     * <p>
     * The negative instances are retrieved from the tmParamsCache: -1 is the latest value in tmParamsCache (the one
     * which was added last), -2 is the previous one and so on.
     * 
     * <p>
     * If {@code allowOld = true} and the {@code tmParams} does not contain a value for {@code param}, then the
     * instances in {@code tmParamsCache} are counted down from 0 instead of -1.
     * <p>
     * If {@code allowOld = false}, the return will always be null if the {@code tmParams} does not contain a value for
     * {@code param}.
     * 
     */
    public ParameterValue getTmParameterInstance(Parameter param, int instance, boolean allowOld) {
        return get(tmParams, tmParamsCache, param, instance, allowOld);
    }

    /**
     * Same as {@link #getTmParameterInstance(Parameter, int, boolean)} but retrieves command parameters from
     * {@code cmdParams} and {@code cmdParamsCache}
     */
    public ParameterValue getCmdParameterInstance(Parameter param, int instance, boolean allowOld) {
        return get(cmdParams, cmdParamsCache, param, instance, allowOld);
    }

    private static ParameterValue get(ParameterValueList tmParams, LastValueCache tmParamsCache, Parameter param,
            int instance, boolean allowOld) {
        if (tmParams == null || tmParams.getFirstInserted(param) == null) {
            if (!allowOld || tmParamsCache == null || instance > 0) {
                return null;
            }
            return tmParamsCache.getValue(param, instance);
        } else {
            if (instance >= 0) {
                return tmParams.get(param, instance);
            } else {
                return tmParamsCache.getValue(param, instance + 1);
            }
        }
    }

    public ArgumentValue getCmdArgument(Argument arg) {
        return (cmdArgs == null) ? null : cmdArgs.get(arg);
    }

    public Map<Argument, ArgumentValue> getCmdArgs() {
        return cmdArgs;
    }

    /**
     * Returns true if the {@code tmParams} or {@code cmdParams} contains a value for {@code param}
     */
    public boolean containsUpdate(Parameter param) {
        if (param.isCommandParameter()) {
            return cmdParams != null && cmdParams.getFirstInserted(param) != null;
        } else {
            return tmParams != null && tmParams.getFirstInserted(param) != null;
        }
    }

    public ParameterValueList getCmdParams() {
        return cmdParams;
    }

    @Override
    public String toString() {
        return "EvaluatorInput [params=" + tmParams
                + ", cmdArgs=" + ((cmdArgs == null) ? null : cmdArgs.values())
                + ", cmdParams=" + cmdParams
                + ", cmdParamsCache=" + cmdParamsCache + "]";
    }
}
