package org.yamcs.tctm.pus.services.tm.one;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public class ServiceOne implements PusService {
    Log log;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    private String yamcsInstance;

    public ServiceOne(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
        initializeSubServices();
    }

    public void initializeSubServices() {
        pusSubServices.put(1, new SubServiceOne(yamcsInstance));
        pusSubServices.put(2, new SubServiceTwo(yamcsInstance));
        pusSubServices.put(3, new SubServiceThree(yamcsInstance));
        pusSubServices.put(4, new SubServiceFour(yamcsInstance));
        pusSubServices.put(5, new SubServiceFive(yamcsInstance));
        pusSubServices.put(6, new SubServiceSix(yamcsInstance));
        pusSubServices.put(7, new SubServiceSeven(yamcsInstance));
        pusSubServices.put(8, new SubServiceEight(yamcsInstance));
        pusSubServices.put(10, new SubServiceTen(yamcsInstance));
    }

    public TmPacket acceptPusPacket(PusTmPacket pusTmPacket) {
        return pusSubServices.get(pusTmPacket.getMessageSubType()).process(pusTmPacket);
    }
}
