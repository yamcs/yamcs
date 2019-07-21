package org.yamcs.api;

import org.yamcs.YConfiguration;

import com.google.common.util.concurrent.AbstractService;

public abstract class AbstractYamcsService extends AbstractService implements YamcsService {

    protected String yamcsInstance;
    protected YConfiguration config;
    protected Log log;

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
        log = new Log(getClass(), yamcsInstance);
    }

    public String getYamcsInstance() {
        return yamcsInstance;
    }

    public YConfiguration getConfig() {
        return config;
    }
}
