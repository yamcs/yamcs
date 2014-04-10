package org.yamcs.tctm;

import org.yamcs.TmProcessor;

import com.google.common.util.concurrent.Service;


/**
 * 
 * @author nm
 * Interface for packets providers
 * 
 */
public interface TmPacketProvider extends Link, Service {
	/**
	 * sets the tm processor that should get all the tm packets
	 */
	public void setTmProcessor(TmProcessor tmProcessor);
	
	/**
	 * true if this is a replay from archive
	 * @return
	 */
	public boolean isArchiveReplay();

}
