package org.yamcs.tctm;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;

/**
 * Interface implemented by components that send commands to the outer universe
 * 
 * @author nm
 *
 */
public interface TcDataLink extends Link {
  
  /**
   * Implement {@link #sendCommand(PreparedCommand)}  instead
   */
    @Deprecated
    void sendTc(PreparedCommand preparedCommand);
    
    boolean sendCommand(PreparedCommand preparedCommand);

    void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryPublisher);
}
