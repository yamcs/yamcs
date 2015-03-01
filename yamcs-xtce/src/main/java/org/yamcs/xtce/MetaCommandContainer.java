package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.List;

public class MetaCommandContainer extends Container {
	private static final long serialVersionUID = 1L;
	public MetaCommandContainer(String name) {
		super(name);
	}
	
	ArrayList<SequenceEntry> entryList =new ArrayList<SequenceEntry>();
	
	/**
	 * From XTCE:
	 * Extend another MetaCommand/CommandContainer.
	 * Generally this item must be set if its MetaCommand is extending another MetaCommand, the reference to the other CommandContainer must be explicitly set here.
	 */
	MetaCommandContainer baseContainer;

	/**
	 * looks up in the argumentEntry list the first one that is linked to the passed on argument
	 * 
	 * @param arg
	 * @return the ArgumentEntry whose aregument is arg. Returns  null if no ArgumentEntry satisfies the condition;
	 */
	public ArgumentEntry getEntryForArgument(Argument arg) {
		for(SequenceEntry se: entryList) {
			if(se instanceof ArgumentEntry) {
				ArgumentEntry ae = (ArgumentEntry)se;
				if(ae.getArgument() == arg) return ae;
			}
		}
		return null;
	}
	
	/**
	 * returns the list of entries
	 * @return
	 */
	public List<SequenceEntry> getEntryList(){
		return entryList;
	}

	public void setBaseContainer(MetaCommandContainer commandContainer) {
		this.baseContainer = commandContainer;		
	}
	
}
