package org.yamcs.nos3;

import java.nio.ByteBuffer;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.utils.TaiUtcConverter;
import org.yamcs.utils.TimeEncoding;

public class Truth42PacketPreprocessor extends AbstractPacketPreprocessor {
    // Constructor used when this preprocessor is used without YAML configuration
    public Truth42PacketPreprocessor(String yamcsInstance) {
        this(yamcsInstance, YConfiguration.emptyConfig());
    }

    // Constructor used when this preprocessor is used with YAML configuration
    // (packetPreprocessorClassArgs)
    public Truth42PacketPreprocessor(String yamcsInstance, YConfiguration config) {
        super(yamcsInstance, config);
    }

    @Override
    public TmPacket process(TmPacket packet) {

        byte[] bytes = packet.getPacket();
        if (bytes.length < 20) { 
            log.warn("Short packet of {} bytes (exepcted at least 20", bytes.length);
            return null;
        }
        
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        short year = bb.getShort();
        short doy = bb.getShort();
        /*short month =*/ bb.getShort();
        /*short day = */bb.getShort();
        short hour = bb.getShort();
        short minute = bb.getShort();
        double second = bb.getDouble();
        int secint = (int) second;
        int millisec = (int) ((second-secint)/1000.0); 
        var dtc = new TaiUtcConverter.DateTimeComponents(year, doy,  hour,  minute,
                 secint,  millisec);            
        var gentime = TimeEncoding.fromUtc(dtc);
        packet.setGenerationTime(gentime);
        return packet;
    }

}
