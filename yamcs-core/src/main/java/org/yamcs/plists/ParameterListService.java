package org.yamcs.plists;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.YConfiguration;

public class ParameterListService extends AbstractYamcsService {

    private ParameterListDb parameterListDb;

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        parameterListDb = new ParameterListDb(yamcsInstance);
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    public ParameterListDb getParameterListDb() {
        return parameterListDb;
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }
}
