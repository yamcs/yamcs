package org.yamcs.tctm;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;

import com.google.common.util.concurrent.Service;

/**
 * Interface implemented by components that send commands to the outer universe
 * @author nm
 *
 */
public interface TcDataLink extends Link, Service {
	void sendTc(PreparedCommand preparedCommand);
	void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener);
}
