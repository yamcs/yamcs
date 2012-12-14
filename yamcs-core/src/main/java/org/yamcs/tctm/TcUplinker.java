package org.yamcs.tctm;

import org.yamcs.cmdhistory.CommandHistory;
import org.yamcs.commanding.PreparedCommand;

import com.google.common.util.concurrent.Service;

/**
 * Interface for sending a telecommand
 * @author nm
 *
 */
public interface TcUplinker extends Link, Service {
	void sendTc(PreparedCommand preparedCommand);

	void setCommandHistoryListener(CommandHistory commandHistoryListener);

	/** 
	 * Returns one of "OK" or "NOK"
	 * @return
	 */
	String getFwLinkStatus();
}
