package org.yamcs.mdb;

import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.commanding.ArgumentValue;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;

/**
 * Keeps track of where we are when filling in the bits and bytes of a command
 * 
 */
public class TcProcessingContext extends ProcessingData {
    final ProcessorData pdata;
    final BitBuffer bitbuf;

    // context parameters and their values
    final private Map<Parameter, Value> paramValues;

    public long generationTime;
    final MetaCommandContainerProcessor mccProcessor;
    final DataEncodingEncoder deEncoder;
    final ArgumentTypeProcessor argumentTypeProcessor;

    private int size;
    final MetaCommand metaCmd;

    public TcProcessingContext(MetaCommand metaCmd, ProcessorData pdata, Map<Parameter, Value> paramValues,
            BitBuffer bitbuf, int bitPosition) {
        super(pdata.getLastValueCache(), null,
                new LinkedHashMap<Argument, ArgumentValue>() /* preserve insertion order */,
                new LastValueCache(), null);
        this.metaCmd = metaCmd;
        this.bitbuf = bitbuf;
        this.pdata = pdata;
        this.paramValues = paramValues;
        this.mccProcessor = new MetaCommandContainerProcessor(this);
        this.deEncoder = new DataEncodingEncoder(this);
        this.argumentTypeProcessor = new ArgumentTypeProcessor(this);
    }

    /**
     * Look up an argument by name only, for cases in which we do not have the full argument definition, such as
     * arguments used for defining the length of other variable-length arguments.
     *
     * @param argName
     *            the name of the argument
     * @return the argument value, if found, or null
     */
    public ArgumentValue getArgumentValue(String argName) {
        for (Map.Entry<Argument, ArgumentValue> entry : cmdArgs.entrySet()) {
            if (argName.equals(entry.getKey().getName())) {
                return entry.getValue();
            }
        }

        return null;
    }

    public Value getRawParameterValue(Parameter param) {
        Value v = paramValues.get(param);
        if (v == null) {
            ParameterValue pv = pdata.getLastValueCache().getValue(param);
            if (pv != null) {
                v = pv.getRawValue();
                if (v == null) {
                    v = pv.getEngValue();
                }
            }

        }
        return v;
    }

    public Map<Argument, ArgumentValue> getArgValues() {
        return cmdArgs;
    }

    public boolean hasArgumentValue(Argument a) {
        return cmdArgs.containsKey(a);
    }

    public void addArgumentValue(Argument a, Value argValue) {
        if (cmdArgs.containsKey(a)) {
            throw new IllegalStateException("There is already a value for argument " + a.getName());
        }
        cmdArgs.put(a, new ArgumentValue(a, argValue));
    }

    public Argument getArgument(String argName) {
        return metaCmd.getEffectiveArgument(argName);
    }

    public int getSize() {
        return size;
    }

    /**
     * sets the size in bytes of the encoded command
     */
    public void setSize(int size) {
        this.size = size;
    }

    /**
     * returns the size in bytes of the encoded command
     */
    public MetaCommand getCommand() {
        return metaCmd;
    }

}
