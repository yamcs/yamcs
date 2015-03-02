package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.Map;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.xtce.Argument;

/**
 * Keeps track of where we are when filling in the bits and bytes of a command
 * @author nm
 *
 */
public class TcProcessingContext {
	
	ByteBuffer bb;
	public int bitPosition;
	
	//Keeps track of the absolute offset of the container where the processing takes place. 
	//Normally 0, but if the processing takes place inside a subcontainer, it reflects the offset of that container with respect to the primary container where the processing started 
	int containerAbsoluteByteOffset;
	
	//arguments and their values - the lists have the same length all the time and arguments correspond one to one to values
	public Map<Argument,Value> argValues;

	
	public long generationTime;
	public MetaCommandContainerProcessor mccProcessor = new MetaCommandContainerProcessor(this);
	public DataEncodingEncoder deEncoder = new DataEncodingEncoder(this);
	
	public TcProcessingContext(ByteBuffer bb, int containerAbsoluteByteOffset, int bitPosition, long generationTime) {
		this.bb = bb;
		this.containerAbsoluteByteOffset=containerAbsoluteByteOffset;
		this.bitPosition = bitPosition;
		this.generationTime = generationTime;
		
	}

	public Value getArgumentValue(Argument arg) {
		return argValues.get(arg);
	}
	
}
