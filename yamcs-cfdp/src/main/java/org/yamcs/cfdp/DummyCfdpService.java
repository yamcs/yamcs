package org.yamcs.cfdp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AbstractService;

public class DummyCfdpService extends AbstractService {

    private static final Logger log = LoggerFactory.getLogger(DummyCfdpService.class);

    @Override
    protected void doStart() {
        log.info("DummyCfdpService.doStart");
    }

    @Override
    protected void doStop() {
        log.info("DummyCfdpService.doStop");
    }

}
