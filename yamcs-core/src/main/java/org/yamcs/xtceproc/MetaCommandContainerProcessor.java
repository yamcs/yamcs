package org.yamcs.xtceproc;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ErrorInCommand;
import org.yamcs.parameter.Value;
import org.yamcs.xtce.*;

public class MetaCommandContainerProcessor {
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    TcProcessingContext pcontext;
    MetaCommandContainerProcessor(TcProcessingContext pcontext) {
        this.pcontext=pcontext;
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
            int previousPosition  = pcontext.bitPosition;
            switch(se.getReferenceLocation()) {
            case previousEntry:
                pcontext.bitPosition += se.getLocationInContainerInBits();
                break;
            case containerStart:
                pcontext.bitPosition = se.getLocationInContainerInBits();
            }
            if(se instanceof ArgumentEntry) {
                fillInArgumentEntry((ArgumentEntry) se, pcontext);
                size = (pcontext.bitPosition+7)/8;
            } else if (se instanceof FixedValueEntry) {
                fillInFixedValueEntry((FixedValueEntry) se, pcontext);
                size = (pcontext.bitPosition+7)/8;
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
        Value rawValue = ArgumentTypeProcessor.decalibrate(atype, argValue);
        
        pcontext.deEncoder.encodeRaw(((BaseDataType) atype).getEncoding(), rawValue);

    }

    private void fillInFixedValueEntry(FixedValueEntry fve, TcProcessingContext pcontext) {
        int sizeInBits = fve.getSizeInBits();

        //CAREFUL: do not change v1 since it is supposed to be final
        final byte[] v1 = fve.getBinaryValue();

        //shift v1 into v2 to be byte aligned with the pcontext.bitPostion	
        int fb1 = sizeInBits&0x07; //number of bits in the leftmost byte in v
        int fb2 = 8 - pcontext.bitPosition&0x07; //number if bits in the leftmost byte in the pcontext

        byte[]v2;

        int bitshift;
        if(fb1>fb2) {//shift to right
            int shift = fb1 - fb2; 
            v2 = new byte[v1.length+1];
            int bits=0;
            int mask = -1>>>(32-shift);

            for(int i = 0; i < v1.length; i++) {
                v2[i] = (byte) (bits | ((v1[i]&0xFF) >> shift));
                bits = (v1[i] & mask) <<(8-shift);
            }
            v2[v1.length] = (byte)bits;
            bitshift = 8-shift; 
        } else if(fb1<fb2){//shift to left
            int shift = fb2 - fb1;
            v2 = new byte[v1.length+1];
            int mask = -1>>>(24+shift);
                int bits=0;
                for(int i = 0; i < v1.length; i++) {
                    v2[i] = (byte) (bits | (v1[i]&0xFF)>>(8-shift));
                    bits = (v1[i] & mask)<<shift;
                }
                v2[v1.length] = (byte)bits;
                bitshift = shift;
        } else {
            v2 = v1;
            bitshift = 0;
        }


        //number of bytes to copy from v2 into pcontext.bb; 
        // the first and last are potentially only partially copied
        //NOTE that v1 and implicitly v2 could potentially have more bytes than required by sizeInBits 
        int bytesToCopy = (bitshift+sizeInBits+7)/8;
        //the first byte in v2 which has to be copied		
        int startByte = v2.length-bytesToCopy; 

        if(bytesToCopy==1) { //special case first and last byte are the same
            byte x = pcontext.bb.get(pcontext.bitPosition/8);
            int mask = (~(-1<<sizeInBits))<<bitshift;
            pcontext.bb.put(pcontext.bitPosition/8, (byte)(x&~mask | (v2[startByte]&mask)));

            pcontext.bitPosition+=sizeInBits;
        } else {
            int bitsToMergeFromFirstByte = (bitshift + sizeInBits) & 0x7; 
            if(bitsToMergeFromFirstByte!=0 ) { //first byte
                byte x = pcontext.bb.get(pcontext.bitPosition/8);
                int mask = ~(-1<<bitsToMergeFromFirstByte);
                pcontext.bb.put(pcontext.bitPosition/8, (byte)(x&~mask | (v2[startByte]&mask)));
                pcontext.bitPosition+=bitsToMergeFromFirstByte;
                bytesToCopy--;
                startByte++;
            }
            if(bitshift>0) bytesToCopy--;

            for(int i=0; i<bytesToCopy; i++) { //the middle part //could be optimised using a bulk put method
                pcontext.bb.put(pcontext.bitPosition/8, v2[startByte+i]);
                pcontext.bitPosition+=8;
            }
            if(bitshift>0) { //last byte
                byte x = pcontext.bb.get(pcontext.bitPosition/8);
                x = (byte) (x & ~(-1<<bitshift));
                pcontext.bb.put(pcontext.bitPosition/8, (byte)(x | v2[v2.length-1]));
                pcontext.bitPosition+=bitshift;
            }
        }

    }
}
