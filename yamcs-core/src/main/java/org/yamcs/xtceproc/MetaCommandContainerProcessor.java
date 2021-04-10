package org.yamcs.xtceproc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ErrorInCommand;
import org.yamcs.commanding.ArgumentValue;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.*;

public class MetaCommandContainerProcessor {
    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    TcProcessingContext pcontext;
    ArgumentTypeProcessor argumentTypeProcessor;

    MetaCommandContainerProcessor(TcProcessingContext pcontext) {
        this.pcontext = pcontext;
        argumentTypeProcessor = new ArgumentTypeProcessor(pcontext.pdata);
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
            if (size > pcontext.size) {
                pcontext.size = size;
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
            if (size > pcontext.size) {
                pcontext.size = size;
            }
        }
    }

    private void fillInArgumentEntry(ArgumentEntry argEntry, TcProcessingContext pcontext) {
        Argument arg = argEntry.getArgument();
        ArgumentValue argValue = pcontext.getArgumentValue(arg);
        if (argValue == null) {
            throw new IllegalStateException("No value for argument " + arg.getName());
        }
        Value engValue = argValue.getEngValue();
        ArgumentType atype = arg.getArgumentType();
        Value rawValue = argumentTypeProcessor.decalibrate(atype, engValue);
        argValue.setRawValue(rawValue);
        encodeRawValue(arg.getName(), atype, rawValue, pcontext);
    }

    private void encodeRawValue(String argName, ArgumentType atype, Value rawValue, TcProcessingContext pcontext) {
        if (atype instanceof BaseDataType) {
            DataEncoding encoding = ((BaseDataType) atype).getEncoding();
            if (encoding == null) {
                throw new CommandEncodingException("No encoding available for type '" + atype.getName()
                        + "' used for argument '" + argName + "'");
            }
            pcontext.deEncoder.encodeRaw(encoding, rawValue);
        } else if (atype instanceof AggregateArgumentType) {
            AggregateArgumentType aggtype = (AggregateArgumentType) atype;
            AggregateValue aggRawValue = (AggregateValue)rawValue;
            for (Member aggm : aggtype.getMemberList()) {
                Value mvalue = aggRawValue.getMemberValue(aggm.getName());
                encodeRawValue(argName+"."+aggm.getName(), (ArgumentType) aggm.getType(), mvalue, pcontext);
            }
        } else {
            throw new CommandEncodingException("Arguments of type "+atype+" not supported");
        }
    }

    private void fillInParameterEntry(ParameterEntry paraEntry, TcProcessingContext pcontext) {
        Parameter para = paraEntry.getParameter();
        Value paraValue = pcontext.getParameterValue(para);
        if (paraValue == null) {
            throw new CommandEncodingException("No value found for parameter '" + para.getName() + "'");
        }

        Value rawValue = paraValue; // TBD if this is correct
        ParameterType ptype = para.getParameterType();
        DataEncoding encoding = ((BaseDataType) ptype).getEncoding();
        if (encoding == null) {
            throw new CommandEncodingException("No encoding available for type '" + ptype.getName()
                    + "' used for parameter '" + para.getName() + "'");
        }
        pcontext.deEncoder.encodeRaw(encoding, rawValue);

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
