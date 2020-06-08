package org.yamcs.archive;

import org.yamcs.YamcsException;
import org.yamcs.yarch.Tuple;

public interface ReplayHandler {

    void setRequest(ReplayOptions req) throws YamcsException;

    String getSelectCmd();

    Object transform(Tuple t);

    /**
     * Called at the end of the replay or during the replay in case the position
     * has to be reset; should clean up any resources that have been initialized
     * in setRequest
     */
    void reset();
}
