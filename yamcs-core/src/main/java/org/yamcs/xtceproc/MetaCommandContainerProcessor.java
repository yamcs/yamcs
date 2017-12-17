package org.yamcs.xtceproc;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ErrorInCommand;
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
        if(parent !=null ) {
            encode(parent);
        }

        MetaCommandContainer container = metaCommand.getCommandContainer();
        if(container == null) {
            throw new ErrorInCommand("MetaCommand has no container: " + metaCommand);
        }

        for(SequenceEntry se: container.getEntryList()) {
            int size=0;
            BitBuffer bitbuf = pcontext.bitbuf;
            switch(se.getReferenceLocation()) {
            case previousEntry:
                bitbuf.setPosition(bitbuf.getPosition() + se.getLocationInContainerInBits());
                break;
            case containerStart:
                bitbuf.setPosition(se.getLocationInContainerInBits());
            }
            if(se instanceof ArgumentEntry) {
                fillInArgumentEntry((ArgumentEntry) se, pcontext);
                size = (bitbuf.getPosition()+7)/8;
            } else if (se instanceof FixedValueEntry) {
                fillInFixedValueEntry((FixedValueEntry) se, pcontext);
                size = (bitbuf.getPosition()+7)/8;
            }
            if(size>pcontext.size) {
                pcontext.size = size;
            }
        } 
    }

    private void fillInArgumentEntry(ArgumentEntry argEntry, TcProcessingContext pcontext) {
        Argument arg = argEntry.getArgument();
        Value argValue = pcontext.getArgumentValue(arg);
        if(argValue==null) {
            throw new IllegalStateException("No value for argument "+arg);
        }

        ArgumentType atype = arg.getArgumentType();
        Value rawValue = argumentTypeProcessor.decalibrate(atype, argValue);
        
        pcontext.deEncoder.encodeRaw(((BaseDataType) atype).getEncoding(), rawValue);

    }

    private void fillInFixedValueEntry(FixedValueEntry fve, TcProcessingContext pcontext) {
        int sizeInBits = fve.getSizeInBits();
        final byte[] v = fve.getBinaryValue();

        //shift v1 into v2 to be byte aligned with the pcontext.bitPostion	
        int fb = sizeInBits&0x07; //number of bits in the leftmost byte in v
        int n = (sizeInBits+7)>>>3;
        BitBuffer bitbuf = pcontext.bitbuf;
        bitbuf.putBits(v[n], fb);
        n--;
        while(n>0) {
            bitbuf.putBits(v[n], fb);
            n--;
        }
    }
}
