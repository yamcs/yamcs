package org.yamcs.archive;


import org.yamcs.InitException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.DeprecationInfo;

@Deprecated
@DeprecationInfo(info = "this class is obsolete, please use org.yamcs.archive.CcsdsTmIndex instead")
public class IndexServer extends CcsdsTmIndex {

    @Override
    public void init(String yamcsInstance, YConfiguration args) throws InitException { 
        super.init(yamcsInstance, args);
    }

    @Override
    protected void doStart() {
        super.doStart();
    }

    @Override
    protected void doStop() {
        super.doStop();
    }

}
