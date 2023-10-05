package org.yamcs.tctm.pus.services.tm.five;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmModifier;

public class ServiceFive implements PusService {
    Log log;
    Map<Integer, PusSubService> pusSubServices = new HashMap<>();
    private String instanceName;

    public ServiceFive(String instanceName, YConfiguration config) {
        this.instanceName = instanceName;
        initializeSubServices();
    
    }

    public void initializeSubServices() {

    }

    @Override
    public TmPacket extractPusModifiers(TmPacket tmPacket) {
        return pusSubServices.get(PusTmModifier.getMessageSubType(tmPacket)).process(tmPacket);
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }
}
