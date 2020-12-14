package org.yamcs.simulator;

import org.yamcs.simulator.pus.PusTmPacket;

public interface PacketFactory {
    SimulatorCcsdsPacket getPacket(byte[] b);
    
    PacketFactory COL_PACKET_FACTORY = new PacketFactory() {
        @Override
        public SimulatorCcsdsPacket getPacket(byte[] b) {
            return new ColumbusCcsdsPacket(b);
        }
    };
    PacketFactory PUS_PACKET_FACTORY = new PacketFactory() {
        @Override
        public SimulatorCcsdsPacket getPacket(byte[] b) {
            return new PusTmPacket(b);
        }
    };
}

