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
	 * @return one of "RT_NORM", "RT_PB", "ARC_NORM", "ARC_PB" to be passed as system variable
	* TODO: remove
	 */
	public String getTmMode();
	
	/**
	 * true if this is a replay from archive
	 * @return
	 */
	public boolean isArchiveReplay();

}
