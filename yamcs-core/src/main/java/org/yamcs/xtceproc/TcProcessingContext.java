package org.yamcs.xtceproc;

import java.util.Map;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.Parameter;

/**
 * Keeps track of where we are when filling in the bits and bytes of a command
 * 
 * @author nm
 *
 */
public class TcProcessingContext {
    final ProcessorData pdata;
    final BitBuffer bitbuf;

    // arguments and their values
    final private Map<Argument, Value> argValues;
    
    //context parameters and their values
    final private Map<Parameter, Value> paramValues;

    public long generationTime;
    final MetaCommandContainerProcessor mccProcessor;
    final DataEncodingEncoder deEncoder;
    public int size;

    public TcProcessingContext(ProcessorData pdata, Map<Argument, Value> argValues, Map<Parameter, Value> paramValues,
            BitBuffer bitbuf, int bitPosition) {
        this.bitbuf = bitbuf;
        this.pdata = pdata;
        this.argValues = argValues;
        this.paramValues = paramValues;
        this.mccProcessor = new MetaCommandContainerProcessor(this);
        this.deEncoder = new DataEncodingEncoder(this);
    }

    public Value getArgumentValue(Argument arg) {
        return argValues.get(arg);
    }

    public Value getParameterValue(Parameter param) {
        Value v = paramValues.get(param); 
        if(v == null) {
            ParameterValue pv = pdata.getLastValueCache().getValue(param);
            if(pv!=null) {
                v = pv.getEngValue();
            }
        }
        return v;
    }
}
