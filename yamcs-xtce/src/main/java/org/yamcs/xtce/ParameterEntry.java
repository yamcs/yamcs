package org.yamcs.xtce;

/**
 * An entry that is a single Parameter
 * @author nm
 *
 */
public class ParameterEntry extends SequenceEntry {
	private static final long serialVersionUID=200805131551L;
	private Parameter parameter;
	
	
	public ParameterEntry(int position,SequenceContainer container,int locationInContainerInBits, ReferenceLocationType location, Parameter parameter) {
		super(position,container,locationInContainerInBits,location);
		this.setParameter(parameter);
	}
	
	/**
	 * Constructor for an unresolved ParameterEntry. The Parameter will come later via setParameter
	 * @param position
	 * @param container
	 * @param locationInContainerInBits
	 * @param location
	 */
	public ParameterEntry(int position,SequenceContainer container,int locationInContainerInBits, ReferenceLocationType location) {
        super(position,container,locationInContainerInBits,location);
    }

	
    public void setParameter(Parameter parameter) {
        this.parameter = parameter;
    }


    public Parameter getParameter() {
        return parameter;
    }
    
    @Override
    public String toString() {
        return "ParameterEntry position:"+getIndex()+", container:"+container.getName()+
            " locationInContainer:"+getLocationInContainerInBits()+" from:"+getReferenceLocation()+
            ", "+parameter+
            ((getRepeatEntry()!=null)?", repeatEntry: ("+getRepeatEntry()+")":"");
    }

}
