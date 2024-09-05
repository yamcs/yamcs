package org.yamcs.tctm.srs3;

import org.yamcs.YConfiguration;
import org.yamcs.security.encryption.SymmetricEncryption;
import org.yamcs.tctm.AbstractPacketPreprocessor;
import org.yamcs.tctm.ErrorDetectionWordCalculator;
import org.yamcs.utils.YObjectLoader;

public class Srs3ManagedParameters {
    byte[] cspHeader;
    int radioHeaderLength;
    byte[] spacecraftId;

    // which error detection algorithm to use (null = no checksum)
    protected ErrorDetectionWordCalculator errorDetectionCalculator;

    // Encryption parameters
    protected SymmetricEncryption se;

    int maxFrameLength;
    boolean enforceFrameLength;

    public Srs3ManagedParameters(YConfiguration config) {
        if (config.containsKey("maxFrameLength"))
            maxFrameLength = config.getInt("maxFrameLength");

        if (config.containsKey("cspHeader")) {
            cspHeader = config.getBinary("cspHeader");
        }

        enforceFrameLength = config.getBoolean("enforceFrameLength", false);

        if (config.containsKey("rhLength"))
            radioHeaderLength = config.getInt("rhLength", 0);

        if (config.containsKey("spacecraftIdSrs"))
            spacecraftId = config.getBinary("spacecraftIdSrs");

        errorDetectionCalculator = AbstractPacketPreprocessor.getErrorDetectionWordCalculator(config);

        if (config.containsKey("encryption")) {
            YConfiguration en = config.getConfig("encryption");

            String className = en.getString("class");
            YConfiguration enConfig = en.getConfigOrEmpty("args");

            se = YObjectLoader.loadObject(className);
            se.init(enConfig);
        }
    }

    public Srs3ManagedParameters(YConfiguration config, int maxFrameLength) {
        this(config);
        this.maxFrameLength = maxFrameLength;
    }

    public Srs3FrameFactory getFrameFactory() {
        return new Srs3FrameFactory(this);
    }

    /**
     * Returns the error detection used for this virtual channel.
     */
    public ErrorDetectionWordCalculator getErrorDetection() {
        return errorDetectionCalculator;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public byte[] getSpacecraftId() {
        return spacecraftId;
    }

    public byte[] getCspHeader() {
        return cspHeader;
    }

    public int getRadioHeaderLength() {
        return radioHeaderLength;
    }

    public SymmetricEncryption getEncryption() {
        return se;
    }
}
