package org.yamcs;

import org.yamcs.logging.Log;

import com.google.common.util.concurrent.AbstractService;

public abstract class AbstractProcessorService extends AbstractService implements ProcessorService {
    protected Processor processor;
    protected YConfiguration config;
    protected Log log;

    @Override
    public void init(Processor proc, YConfiguration config, Object spec) {
        this.processor = proc;
        this.config = config;
        log = new Log(getClass(), proc.getInstance());
        log.setContext(proc.getName());
    }

    public String getYamcsInstance() {
        return processor.getInstance();
    }

    public YConfiguration getConfig() {
        return config;
    }
}
