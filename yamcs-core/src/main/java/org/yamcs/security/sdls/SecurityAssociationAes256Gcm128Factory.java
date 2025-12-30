package org.yamcs.security.sdls;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;

/**
 * Creates instances of {@link SecurityAssociationAes256Gcm128}
 */
public class SecurityAssociationAes256Gcm128Factory implements SdlsSecurityAssociationFactory {

    public SecurityAssociationAes256Gcm128Factory() {
    }

    /**
     * @return the configuration spec defining the properties of {@link SecurityAssociationAes256Gcm128} that can be
     * customized.
     */
    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("keyFile", OptionType.STRING);
        spec.addOption("verifySeqNum", OptionType.BOOLEAN).withDefault(false);
        spec.addOption("seqNumWindow", OptionType.INTEGER).withDefault(10);
        spec.addOption("initialSeqNum", OptionType.STRING).withDefault(null);
        return spec;
    }

    @Override
    public SdlsSecurityAssociation create(String instanceName, String linkName, short spi, YConfiguration args) {
        byte[] sdlsKey;
        try {
            sdlsKey = Files.readAllBytes(Path.of(args.getString("keyFile")));
        } catch (IOException e) {
            throw new ConfigurationException(e);
        }

        Spec spec = getSpec();
        boolean verifySeqNum = args.containsKey("verifySeqNum") ?
                args.getBoolean("verifySeqNum")
                : (boolean) spec.getOption("verifySeqNum").getDefaultValue();

        int encryptionSeqNumWindow = args.containsKey("seqNumWindow") ?
                Math.abs(args.getInt("seqNumWindow"))
                : (int) spec.getOption("seqNumWindow").getDefaultValue();

        byte[] initialSeqNum = args.containsKey("initialSeqNum") ?
                args.getBinary("initialSeqNum", null)
                : (byte[]) spec.getOption("initialSeqNum").getDefaultValue();
        return new SecurityAssociationAes256Gcm128(instanceName, linkName, sdlsKey, spi, initialSeqNum,
                encryptionSeqNumWindow, verifySeqNum);
    }
}