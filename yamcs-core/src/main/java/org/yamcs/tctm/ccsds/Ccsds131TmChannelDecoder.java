package org.yamcs.tctm.ccsds;

import java.nio.ByteBuffer;

import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.TmChannelDecoder;

/** Reed-Solomon and derandomization component based on CCSDS 131.0-B. */
public class Ccsds131TmChannelDecoder implements TmChannelDecoder {
    private CcsdsFrameDecoder decoder;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("codec", OptionType.STRING).withChoices("NONE", "RS").withDefault("NONE");
        spec.addOption("errorCorrectionCapability", OptionType.INTEGER).withDefault(16);
        spec.addOption("interleavingDepth", OptionType.INTEGER).withDefault(5);
        spec.addOption("derandomize", OptionType.BOOLEAN).withDefault(false);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String linkName, YConfiguration args) {
        decoder = new CcsdsFrameDecoder(args);
    }

    @Override
    public ByteBuffer decode(ByteBuffer frame) throws TcTmException {
        if (!frame.hasArray() || frame.isReadOnly()) {
            throw new TcTmException("CCSDS 131 channel decoder requires a writable array-backed ByteBuffer");
        }

        int offset = frame.arrayOffset() + frame.position();
        final int decodedLength;
        try {
            decodedLength = decoder.decodeFrame(frame.array(), offset, frame.remaining());
        } catch (IllegalArgumentException e) {
            throw new TcTmException("Error decoding physical-channel frame: " + e.getMessage(), e);
        }
        if (decodedLength == -1) {
            throw new TcTmException("Physical-channel error correction failed");
        }
        ByteBuffer decoded = frame.slice();
        decoded.limit(decodedLength);
        return decoded;
    }

    @Override
    public int encodedFrameLength() {
        return decoder.encodedFrameLength();
    }

    @Override
    public int decodedFrameLength() {
        return decoder.decodedFrameLength();
    }
}
