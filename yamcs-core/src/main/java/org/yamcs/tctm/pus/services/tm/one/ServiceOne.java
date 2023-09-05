package org.yamcs.tctm.pus.services.tm.one;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public class ServiceOne implements PusService {
    Log log;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    private String instanceName;

    public ServiceOne(String instanceName) {
        this.instanceName = instanceName;
        initializeSubServices();
    }

    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(this.instanceName));
        pusSubServices.put(2, new SubServiceTwo(this.instanceName));
        pusSubServices.put(3, new SubServiceThree(this.instanceName));
        pusSubServices.put(4, new SubServiceFour(this.instanceName));
        pusSubServices.put(5, new SubServiceFive(this.instanceName));
        pusSubServices.put(6, new SubServiceSix(this.instanceName));
        pusSubServices.put(7, new SubServiceSeven(this.instanceName));
        pusSubServices.put(8, new SubServiceEight(this.instanceName));
        pusSubServices.put(10, new SubServiceTen(this.instanceName));
    }

    public void acceptPusPacket(PusTmPacket pusTmPacket) {
        pusSubServices.get(pusTmPacket.getMessageSubType()).process(pusTmPacket);
    }
}
