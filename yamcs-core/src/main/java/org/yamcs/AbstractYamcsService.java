package org.yamcs;

import org.yamcs.logging.Log;

import com.google.common.util.concurrent.AbstractService;

public abstract class AbstractYamcsService extends AbstractService implements YamcsService {
    protected String yamcsInstance;
    protected String serviceName;
    protected YConfiguration config;
    protected Log log;

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        this.yamcsInstance = yamcsInstance;
        this.serviceName = serviceName;
        this.config = config;
        log = new Log(getClass(), yamcsInstance);
    }

    @Override
    public String getYamcsInstance() {
        return yamcsInstance;
    }

    public YConfiguration getConfig() {
        return config;
    }
}
