package org.yamcs.tctm.pus.services.tm.five;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public class ServiceFive implements PusService {
    Log log;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    private String instanceName;

    public ServiceFive(String instanceName) {
        this.instanceName = instanceName;
        initializeSubServices();
    
    }


    public void initializeSubServices() {

    }


    @Override
    public void acceptPusPacket(PusTmPacket pusTmPacket) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'acceptPusPacket'");
    }
}
