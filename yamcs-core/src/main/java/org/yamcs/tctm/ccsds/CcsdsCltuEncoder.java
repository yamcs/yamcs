package org.yamcs.tctm.ccsds;

import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TcChannelEncoder;
import org.yamcs.tctm.ccsds.error.BchCltuGenerator;
import org.yamcs.tctm.ccsds.error.CltuGenerator;
import org.yamcs.tctm.ccsds.error.Ldpc256CltuGenerator;
import org.yamcs.tctm.ccsds.error.Ldpc64CltuGenerator;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.YObjectLoader;

/** CCSDS CLTU encoding component for a composed TC frame link. */
public class CcsdsCltuEncoder implements TcChannelEncoder {
    private CltuGenerator generator;
    private boolean randomize;
    private IntArray skipRandomizationForVcs;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("cltuEncoding", OptionType.STRING).withRequired(true)
                .withChoices("BCH", "LDPC64", "LDPC256", "CUSTOM");
        spec.addOption("cltuStartSequence", OptionType.STRING);
        spec.addOption("cltuTailSequence", OptionType.STRING);
        spec.addOption("randomizeCltu", OptionType.BOOLEAN);
        spec.addOption("skipRandomizationForVcs", OptionType.LIST).withElementType(OptionType.INTEGER);
        spec.addOption("cltuGeneratorClassName", OptionType.STRING);
        spec.addOption("cltuGeneratorArgs", OptionType.MAP).withSpec(Spec.ANY);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String linkName, YConfiguration args) {
        String encoding = args.getString("cltuEncoding");
        switch (encoding) {
        case "BCH":
            generator = new BchCltuGenerator(
                    args.getBinary("cltuStartSequence", BchCltuGenerator.CCSDS_START_SEQ),
                    args.getBinary("cltuTailSequence", BchCltuGenerator.CCSDS_TAIL_SEQ));
            randomize = args.getBoolean("randomizeCltu", false);
            break;
        case "LDPC64":
            requireLdpcRandomization(args);
            generator = new Ldpc64CltuGenerator(
                    args.getBinary("cltuStartSequence", Ldpc64CltuGenerator.CCSDS_START_SEQ),
                    args.getBinary("cltuTailSequence", CltuGenerator.EMPTY_SEQ));
            randomize = true;
            break;
        case "LDPC256":
            requireLdpcRandomization(args);
            generator = new Ldpc256CltuGenerator(
                    args.getBinary("cltuStartSequence", Ldpc256CltuGenerator.CCSDS_START_SEQ),
                    args.getBinary("cltuTailSequence", CltuGenerator.EMPTY_SEQ));
            randomize = true;
            break;
        case "CUSTOM":
            String className = args.getString("cltuGeneratorClassName", null);
            if (className == null) {
                throw new ConfigurationException("CUSTOM CLTU encoding requires cltuGeneratorClassName");
            }
            generator = args.containsKey("cltuGeneratorArgs")
                    ? YObjectLoader.loadObject(className, args.getConfig("cltuGeneratorArgs"))
                    : YObjectLoader.loadObject(className);
            randomize = args.getBoolean("randomizeCltu", false);
            break;
        default:
            throw new ConfigurationException("Unsupported CLTU encoding " + encoding);
        }

        if (args.containsKey("skipRandomizationForVcs")) {
            List<Integer> vcIds = args.getList("skipRandomizationForVcs");
            if (!vcIds.isEmpty()) {
                skipRandomizationForVcs = IntArray.wrap(vcIds.stream().mapToInt(Integer::intValue).toArray());
                skipRandomizationForVcs.sort();
            }
        }
    }

    @Override
    public byte[] encode(int virtualChannelId, byte[] data) {
        boolean applyRandomization = randomize && (skipRandomizationForVcs == null
                || skipRandomizationForVcs.binarySearch(virtualChannelId) < 0);
        return generator.makeCltu(data, applyRandomization);
    }

    private static void requireLdpcRandomization(YConfiguration args) {
        if (!args.getBoolean("randomizeCltu", true)) {
            throw new ConfigurationException(
                    "CLTU randomization is always enabled for LDPC; remove randomizeCltu: false");
        }
    }
}
