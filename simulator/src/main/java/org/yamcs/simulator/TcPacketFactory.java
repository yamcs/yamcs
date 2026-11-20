package org.yamcs.simulator;

import org.yamcs.simulator.pus.PusPacketConfig;
import org.yamcs.utils.ByteArrayUtils;

public interface TcPacketFactory {
    SimulatorCcsdsPacket getPacket(byte[] b);

    default int getPacketLength(byte[] frame, int offset, int remaining) {
        return remaining;
    }
    
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
    TcPacketFactory PUS_PACKET_FACTORY = pusPacketFactory(PusPacketConfig.DEFAULT);

    static TcPacketFactory pusPacketFactory(PusPacketConfig config) {
        return new TcPacketFactory() {
            @Override
            public SimulatorCcsdsPacket getPacket(byte[] packet) {
                return config.wrapTc(packet);
            }

            @Override
            public int getPacketLength(byte[] frame, int offset, int remaining) {
                return ByteArrayUtils.decodeUnsignedShort(frame, offset + 4) + 7;
            }

            @Override
            public int getMinLength() {
                return 7;
            }
        };
    }
    int getMinLength();
}

