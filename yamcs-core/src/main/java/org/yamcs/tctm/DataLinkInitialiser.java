package org.yamcs.tctm;

import org.yamcs.AbstractYamcsService;
import org.yamcs.utils.DeprecationInfo;

@DeprecationInfo(info = "This service is deprecated, please remove it from the configuration. "
        + "The links are automatically initialized by the DataLinkManager if the dataLinks section "
        + "is present in the configuration file.")
@Deprecated
public class DataLinkInitialiser extends AbstractYamcsService {

    @Override
    protected void doStart() {
       notifyStarted();
        
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }
}
