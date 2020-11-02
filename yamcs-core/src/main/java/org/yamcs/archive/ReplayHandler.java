package org.yamcs.archive;

import org.yamcs.YamcsException;
import org.yamcs.yarch.Tuple;

public interface ReplayHandler {

    void setRequest(ReplayOptions req) throws YamcsException;

    String getSelectCmd();

    Object transform(Tuple t);

}
