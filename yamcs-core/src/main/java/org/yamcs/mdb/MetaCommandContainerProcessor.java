package org.yamcs.mdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ErrorInCommand;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.ArrayDataType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.Container;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceEntry;

public class MetaCommandContainerProcessor {
    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    TcProcessingContext pcontext;

    MetaCommandContainerProcessor(TcProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    public void encode(MetaCommand metaCommand) throws ErrorInCommand {
        MetaCommand parent = metaCommand.getBaseMetaCommand();
        if (parent != null) {
            encode(parent);
        }

        CommandContainer container = metaCommand.getCommandContainer();
        if (container == null) {
            throw new ErrorInCommand("MetaCommand has no container: " + metaCommand.getQualifiedName());
        }

        if (parent == null) { // strange case for inheriting only the container without a command
            Container baseContainer = container.getBaseContainer();
            if (baseContainer != null) {
                encode(baseContainer);
            }
        }

        for (SequenceEntry se : container.getEntryList()) {

            int size = 0;
            BitBuffer bitbuf = pcontext.bitbuf;
            switch (se.getReferenceLocation()) {
            case PREVIOUS_ENTRY:
                bitbuf.setPosition(bitbuf.getPosition() + se.getLocationInContainerInBits());
                break;
            case CONTAINER_START:
                bitbuf.setPosition(se.getLocationInContainerInBits());
            }
            if (se instanceof ArgumentEntry) {
                fillInArgumentEntry((ArgumentEntry) se, pcontext);
                size = (bitbuf.getPosition() + 7) / 8;
            } else if (se instanceof FixedValueEntry) {
                fillInFixedValueEntry((FixedValueEntry) se, pcontext);
                size = (bitbuf.getPosition() + 7) / 8;
            } else if (se instanceof ParameterEntry) {
                fillInParameterEntry((ParameterEntry) se, pcontext);
                size = (bitbuf.getPosition() + 7) / 8;
            }
            if (size > pcontext.getSize()) {
                pcontext.setSize(size);
            }
        }
    }

    private void encode(Container container) {
        Container baseContainer = container.getBaseContainer();
        if (baseContainer != null) {
            encode(baseContainer);
        }
        for (SequenceEntry se : container.getEntryList()) {
            int size = 0;
            BitBuffer bitbuf = pcontext.bitbuf;
            switch (se.getReferenceLocation()) {
            case PREVIOUS_ENTRY:
                bitbuf.setPosition(bitbuf.getPosition() + se.getLocationInContainerInBits());
                break;
            case CONTAINER_START:
                bitbuf.setPosition(se.getLocationInContainerInBits());
            }
            if (se instanceof ArgumentEntry) {
                fillInArgumentEntry((ArgumentEntry) se, pcontext);
                size = (bitbuf.getPosition() + 7) / 8;
            } else if (se instanceof FixedValueEntry) {
                fillInFixedValueEntry((FixedValueEntry) se, pcontext);
                size = (bitbuf.getPosition() + 7) / 8;
            } else if (se instanceof ParameterEntry) {
                fillInParameterEntry((ParameterEntry) se, pcontext);
                size = (bitbuf.getPosition() + 7) / 8;
            }
            if (size > pcontext.getSize()) {
                pcontext.setSize(size);
            }
        }
    }

    private void fillInArgumentEntry(ArgumentEntry argEntry, TcProcessingContext pcontext) {
        Argument arg = argEntry.getArgument();
        ArgumentValue argValue = pcontext.getCmdArgument(arg);
        if (argValue == null) {
            throw new IllegalStateException("No value for argument " + arg.getName());
        }
        Value engValue = argValue.getEngValue();
        ArgumentType atype = arg.getArgumentType();
        Value rawValue = pcontext.argumentTypeProcessor.decalibrate(atype, engValue);
        argValue.setRawValue(rawValue);
        encodeRawValue(arg.getName(), atype, rawValue, pcontext);
    }

    private void encodeRawValue(String argName, DataType type, Value rawValue, TcProcessingContext pcontext) {
        if (type instanceof BaseDataType) {
            DataEncoding encoding = ((BaseDataType) type).getEncoding();
            if (encoding == null) {
                throw new CommandEncodingException("No encoding available for type '" + type.getName()
                        + "' used for argument '" + argName + "'");
            }
            pcontext.deEncoder.encodeRaw(encoding, rawValue);
        } else if (type instanceof AggregateDataType) {
            AggregateDataType aggtype = (AggregateDataType) type;
            AggregateValue aggRawValue = (AggregateValue) rawValue;
            for (Member aggm : aggtype.getMemberList()) {
                Value mvalue = aggRawValue.getMemberValue(aggm.getName());
                encodeRawValue(argName + "." + aggm.getName(), aggm.getType(), mvalue, pcontext);
            }
        } else if (type instanceof ArrayDataType) {
            ArrayDataType arrtype = (ArrayDataType) type;
            DataType etype = arrtype.getElementType();
            ArrayValue arrayRawValue = (ArrayValue) rawValue;
            for (int i = 0; i < arrayRawValue.flatLength(); i++) {
                Value valuei = arrayRawValue.getElementValue(i);
                encodeRawValue(argName + arrayRawValue.flatIndexToString(i), etype, valuei, pcontext);
            }
        } else {
            throw new CommandEncodingException("Arguments or parameters of type " + type + " not supported");
        }
    }

    private void fillInParameterEntry(ParameterEntry paraEntry, TcProcessingContext pcontext) {
        Parameter para = paraEntry.getParameter();
        Value paraValue = pcontext.getRawParameterValue(para);
        if (paraValue == null) {
            throw new CommandEncodingException("No value found for parameter '" + para.getName() + "'");
        }

        Value rawValue = paraValue; // TBD if this is correct
        ParameterType ptype = para.getParameterType();
        encodeRawValue(para.getQualifiedName(), ptype, rawValue, pcontext);
    }

    private void fillInFixedValueEntry(FixedValueEntry fve, TcProcessingContext pcontext) {
        int sizeInBits = fve.getSizeInBits();
        final byte[] v = fve.getBinaryValue();

        int fb = sizeInBits & 0x07; // number of bits in the leftmost byte in v
        int n = (sizeInBits + 7) >>> 3;
        BitBuffer bitbuf = pcontext.bitbuf;
        int i = v.length - n;
        if (fb > 0) {
            bitbuf.putBits(v[i++], fb);
        }
        while (i < v.length) {
            bitbuf.putBits(v[i++], 8);
        }
    }
}
