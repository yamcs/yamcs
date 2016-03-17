package org.yamcs.tctm;

import com.google.common.util.concurrent.Service;


/**
 * 
 * @author nm
 * 
 * Interface for components reading packets from external parties.
 * 
 */
public interface TmPacketDataLink extends Link, Service {
	/**
	 * sets the tm processor that should get all the tm packets
	 */
	public void setTmSink(TmSink tmSink);
	
	/**
	 * true if this is a replay from archive
	 * @return
	 */
	public boolean isArchiveReplay();

}
