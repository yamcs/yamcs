package org.yamcs.simulator.pus;

import org.yamcs.ConfigurationException;

/** Packet-layer settings shared by the simulator's transport and PUS services. */
public record PusPacketConfig(int apid, int spareBytes, boolean tmCrc, boolean tcCrc, boolean pus11Crc,
        boolean pus22Crc, boolean embeddedTcCrc, int packetStoreSizeUnitBytes) {
    public static final int DEFAULT_PACKET_STORE_SIZE_UNIT_BYTES = 1024;
    public static final PusPacketConfig DEFAULT = new PusPacketConfig(1, 4, true, true, true, true);

    public PusPacketConfig {
        if (apid < 1 || apid >= 0x7ff) {
            throw new ConfigurationException("PUS APID must be in [1, 2046]");
        }
        if (spareBytes != 0 && spareBytes != 4) {
            throw new ConfigurationException("PUS spareBytes must be 0 or 4");
        }
        if (packetStoreSizeUnitBytes < 1) {
            throw new ConfigurationException("PUS packetStoreSizeUnitBytes must be positive");
        }
    }

    public PusPacketConfig(int apid, int spareBytes, boolean tmCrc, boolean tcCrc, boolean pus11Crc,
            boolean pus22Crc, boolean embeddedTcCrc) {
        this(apid, spareBytes, tmCrc, tcCrc, pus11Crc, pus22Crc, embeddedTcCrc, DEFAULT_PACKET_STORE_SIZE_UNIT_BYTES);
    }

    public PusPacketConfig(int apid, int spareBytes, boolean tmCrc, boolean tcCrc, boolean pus11Crc,
            boolean embeddedTcCrc) {
        this(apid, spareBytes, tmCrc, tcCrc, pus11Crc, tcCrc, embeddedTcCrc);
    }

    public PusTcPacket wrapTc(byte[] packet) {
        boolean crc = tcCrc;
        if (packet.length >= 11 && packet[8] == 4) {
            if (packet[7] == 11) {
                crc = pus11Crc;
            } else if (packet[7] == 22) {
                crc = pus22Crc;
            }
        }
        return new PusTcPacket(packet, spareBytes, crc);
    }
}
