package org.yamcs.tctm.pus.services.tm.seventeen;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;

public class SubServiceTwo implements PusSubService {

    String yamcsInstance;
    YConfiguration config;

    public SubServiceTwo(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        this.config = config;
    }

    @Override
    public PreparedCommand process(PreparedCommand telecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        byte[] binary = tmPacket.getPacket();

        // Add Alive indicator
        ByteBuffer bb = ByteBuffer.wrap(new byte[binary.length + 1]);
        bb.put(binary);
        bb.put((byte) 0);   // This is the indicator used in the TM Parameter that the onboard Node is alive

        TmPacket newPacket = new TmPacket(tmPacket.getReceptionTime(), tmPacket.getGenerationTime(),
                tmPacket.getSeqCount(), bb.array());
        newPacket.setEarthReceptionTime(tmPacket.getEarthReceptionTime());

        ArrayList<TmPacket> pPkts = new ArrayList<>();
        pPkts.add(newPacket);

        return pPkts;
    } 
}
