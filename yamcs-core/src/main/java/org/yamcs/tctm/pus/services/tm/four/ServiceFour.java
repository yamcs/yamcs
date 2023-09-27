package org.yamcs.tctm.pus.services.tm.four;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public class ServiceFour implements PusService {
    Log log;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    private String instanceName;

    public ServiceFour(String instanceName) {
        this.instanceName = instanceName;
        initializeSubServices();
    
    }


    public void initializeSubServices() {

    }


    @Override
    public TmPacket acceptPusPacket(PusTmPacket pusTmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'acceptPusPacket'");
    }
}
