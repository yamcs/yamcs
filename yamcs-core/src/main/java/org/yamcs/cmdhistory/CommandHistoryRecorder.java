package org.yamcs.cmdhistory;

import org.yamcs.utils.DeprecationInfo;

@Deprecated
@DeprecationInfo(info="Use the class with the same name from the org.yamcs.archive package")
public class CommandHistoryRecorder extends org.yamcs.archive.CommandHistoryRecorder {

    public CommandHistoryRecorder(String instance) {
        super(instance);
    }

}
