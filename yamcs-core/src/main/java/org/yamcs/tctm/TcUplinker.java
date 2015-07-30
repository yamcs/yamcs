package org.yamcs.tctm;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;

import com.google.common.util.concurrent.Service;

/**
 * Interface for sending a telecommand
 * @author nm
 *
 */
public interface TcUplinker extends Link, Service {
	void sendTc(PreparedCommand preparedCommand);
	void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener);
}
