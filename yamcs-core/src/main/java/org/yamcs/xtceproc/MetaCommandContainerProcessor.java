package org.yamcs.xtceproc;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.MetaCommandContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;

public class MetaCommandContainerProcessor {
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    TcProcessingContext pcontext;
    MetaCommandContainerProcessor(TcProcessingContext pcontext) {
        this.pcontext=pcontext;
    }
    
    public void encode(MetaCommandContainer container) {
		for(SequenceEntry se: container.getEntryList()) {
			switch(se.getReferenceLocation()) {
            case previousEntry:
                pcontext.bitPosition+=se.getLocationInContainerInBits();
                break;
            case containerStart:
                pcontext.bitPosition=se.getLocationInContainerInBits();
            }
			if(se instanceof ArgumentEntry) {
				fillInArgumentEntry((ArgumentEntry) se, pcontext);
			} else if (se instanceof FixedValueEntry) {
				fillInFixedValueEntry((FixedValueEntry) se, pcontext);
			}			
        } 
	}
	
	private void fillInArgumentEntry(ArgumentEntry argEntry, TcProcessingContext pcontext) {
		Argument arg = argEntry.getArgument();
		Value argValue = pcontext.getArgumentValue(arg);
		if(argValue==null) {
			throw new IllegalStateException("No value for argument "+arg);
		}
		ReferenceLocationType rlt = argEntry.getReferenceLocation();
		switch(rlt) {
		case containerStart: 
			pcontext.bitPosition = argEntry.getLocationInContainerInBits();
			break;
		case previousEntry:
			pcontext.bitPosition += argEntry.getLocationInContainerInBits();
			break;
		}
		
		ArgumentType atype = arg.getArgumentType();
		Value rawValue = ArgumentTypeProcessor.decalibrate(atype, argValue);
		pcontext.deEncoder.encodeRaw(((BaseDataType)atype).getEncoding(), rawValue);		
	}
		
	private void fillInFixedValueEntry(FixedValueEntry fvEntry, TcProcessingContext pcontext) {
		
	}
 
}
