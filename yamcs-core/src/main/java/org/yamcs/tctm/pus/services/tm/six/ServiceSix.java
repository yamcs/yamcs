package org.yamcs.tctm.pus.services.tm.six;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusService;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;

public class ServiceSix implements PusService {
    Log log;
    Map<Integer, PusSubService> subServices = new HashMap<>();
    String yamcsInstance;
    YConfiguration config;

    public ServiceSix(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;

        initializeSubServices();
    }

    public void initializeSubServices() {
        subServices.put(4, new SubServiceFour(yamcsInstance, config.getConfigOrEmpty("four")));
        subServices.put(6, new SubServiceSix(yamcsInstance, config.getConfigOrEmpty("six")));
        subServices.put(8, new SubServiceEight(yamcsInstance, config.getConfigOrEmpty("eight")));
        subServices.put(10, new SubServiceTen(yamcsInstance, config.getConfigOrEmpty("ten")));
        subServices.put(18, new SubServiceEighteen(yamcsInstance, config.getConfigOrEmpty("eighteen")));
    }

    @Override
    public ArrayList<TmPacket> extractPusModifiers(TmPacket tmPacket) {
        return subServices.get(PusTmCcsdsPacket.getMessageSubType(tmPacket.getPacket())).process(tmPacket);
    }

    @Override
    public PreparedCommand addPusModifiers(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPusModifiers'");
    }
}