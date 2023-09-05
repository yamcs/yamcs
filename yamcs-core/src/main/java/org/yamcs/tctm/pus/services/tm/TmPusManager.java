package org.yamcs.tctm.pus.services.tm;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.tm.one.ServiceOne;
import org.yamcs.tctm.pus.services.tm.two.ServiceTwo;
import org.yamcs.tctm.pus.services.tm.three.ServiceThree;
import org.yamcs.tctm.pus.services.tm.four.ServiceFour;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.tm.five.ServiceFive;

public class TmPusManager {
    Log log;
    final String yamcsInstance;
    Map<Integer, PusService> pusServices = new HashMap<>();

    public TmPusManager(String instanceName) {
        this.yamcsInstance = instanceName;
        initializePUSServices();
    }

    private void initializePUSServices() {
        pusServices.put(1, new ServiceOne(this.yamcsInstance));
        pusServices.put(2, new ServiceTwo(this.yamcsInstance));
        pusServices.put(3, new ServiceThree(this.yamcsInstance));
        pusServices.put(4, new ServiceFour(this.yamcsInstance));
        pusServices.put(5, new ServiceFive(this.yamcsInstance));
    }

    public void acceptTmPacket(TmPacket tmPacket) {
        PusTmPacket pusTmPacket = new PusTmPacket(tmPacket);
        pusServices.get(pusTmPacket.getMessageType()).acceptPusPacket(pusTmPacket);
    }
}
