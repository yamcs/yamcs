package org.yamcs.xtce;

/**
 * An entry that is a single Argument
 * 
 * @author nm
 *
 */
public class ArgumentEntry extends SequenceEntry {
	private static final long serialVersionUID = 1L;
	private Argument argument;
	
	
	public ArgumentEntry(int position, MetaCommandContainer container, int locationInContainerInBits, ReferenceLocationType location, Argument argument) {
		super(position, container, locationInContainerInBits, location);
		this.argument = argument;
	}
	
	/**
	 * Constructor for an unresolved ParameterEntry. The Parameter will come later via setParameter
	 * @param position
	 * @param container
	 * @param locationInContainerInBits
	 * @param location
	 */
	public ArgumentEntry(int position, MetaCommandContainer container, int locationInContainerInBits, ReferenceLocationType location) {
        super(position, container, locationInContainerInBits, location);
    }

	
    public void setArgument(Argument argument) {
        this.argument = argument;
    }


    public Argument getArgument() {
        return argument;
    }
    
    @Override
    public String toString() {
        return "ArgumentEntry position:"+getIndex()+", container:"+container.getName()+
            " locationInContainer:"+getLocationInContainerInBits()+" from:"+getReferenceLocation()+
            ", argument: "+argument;
            
    }
}