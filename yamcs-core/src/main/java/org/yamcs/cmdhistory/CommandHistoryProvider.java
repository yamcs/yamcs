package org.yamcs.cmdhistory;

import com.google.common.util.concurrent.Service;

/**
 * Interface implemented by all classes that provide command history to the {@link CommandHistoryRequestManager}
 * 
 * @author nm
 *
 */
public interface CommandHistoryProvider extends Service {
    public void setCommandHistoryRequestManager(CommandHistoryRequestManager chrm);
}
