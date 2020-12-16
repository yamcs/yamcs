package org.yamcs.simulator;

import org.yamcs.simulator.pus.PusTcPacket;

public interface TcPacketFactory {
    SimulatorCcsdsPacket getPacket(byte[] b);
    
    TcPacketFactory COL_PACKET_FACTORY = new TcPacketFactory() {
        @Override
        public SimulatorCcsdsPacket getPacket(byte[] b) {
            return new ColumbusCcsdsPacket(b);
        }

        @Override
        public int getMinLength() {
            return 16;
        }
    };
    TcPacketFactory PUS_PACKET_FACTORY = new TcPacketFactory() {
        @Override
        public SimulatorCcsdsPacket getPacket(byte[] b) {
            return new PusTcPacket(b);
        }

        @Override
        public int getMinLength() {
            return 13;
        }
        
    };
    int getMinLength();
}

