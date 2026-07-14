package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.ccsds.time.CucTimeDecoder;
import org.yamcs.tctm.ccsds.time.CucTimeEncoder;

public class PusTimeEncoding {
    public enum Epoch {
        SIMULATOR,
        UNIX
    }

    public static final PusTimeEncoding DEFAULT = new PusTimeEncoding(0x2F, -1, false, Epoch.SIMULATOR);

    private static final double DRIFT = 1 + 1e-7;

    private final CucTimeEncoder encoder;
    private final CucTimeDecoder decoder;
    private final Epoch epoch;
    private final long t0 = System.nanoTime();

    public PusTimeEncoding(int pfield, int pfieldCont, boolean implicitPfield, Epoch epoch) {
        this.encoder = new CucTimeEncoder(pfield, pfieldCont, implicitPfield);
        this.decoder = new CucTimeDecoder(implicitPfield ? pfield : -1, implicitPfield ? pfieldCont : -1);
        this.epoch = epoch;
    }

    public static PusTimeEncoding fromConfig(YConfiguration config) {
        String type = config.getString("type", "CUC");
        if (!"CUC".equalsIgnoreCase(type)) {
            throw new ConfigurationException("Unsupported PUS time encoding type '" + type + "'. Only CUC is supported");
        }

        int pfield = config.getInt("pfield", 0x2F);
        int pfieldCont = config.getInt("pfieldCont", -1);
        boolean implicitPfield = config.getBoolean("implicitPfield", false);
        Epoch epoch = config.getEnum("epoch", Epoch.class, Epoch.SIMULATOR);
        return new PusTimeEncoding(pfield, pfieldCont, implicitPfield, epoch);
    }

    public int getEncodedLength() {
        return encoder.getEncodedLength();
    }

    public PusTime now() {
        if (epoch == Epoch.UNIX) {
            return new PusTime(System.currentTimeMillis());
        } else {
            long nanos = System.nanoTime() - t0;
            long millis = (long) ((nanos / 1_000_000.0) * DRIFT);
            return new PusTime(millis);
        }
    }

    public void encode(PusTime time, ByteBuffer bb) {
        int offset = bb.position();
        int n = encoder.encode(time.millis(), bb.array(), bb.arrayOffset() + offset);
        bb.position(offset + n);
    }

    public PusTime read(ByteBuffer bb) {
        int offset = bb.position();
        long millis = decoder.decode(bb.array(), bb.arrayOffset() + offset);
        bb.position(offset + getEncodedLength());
        return new PusTime(millis);
    }
}
