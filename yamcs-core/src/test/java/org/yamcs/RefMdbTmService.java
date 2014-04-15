package org.yamcs;

import org.yamcs.tctm.AbstractTcTmService;

public class RefMdbTmService extends AbstractTcTmService {
    public RefMdbTmService(RefMdbPacketGenerator tmGenerator) throws ConfigurationException {
        this.tm=tmGenerator;
    }
}