package org.yamcs.simulator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.mdb.SpaceSystemLoader;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
//import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.util.DoubleRange;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.SpaceSystem;
//import org.yamcs.xtce.StringDataEncoding;
//import org.yamcs.xtce.StringParameterType;

/**
 * Generates a MDB used for performance testing.
 * 
 * It generates all unsigned integer parameters with the size in bits specified
 * 
 * @author nm
 *
 */
public class PerfMdbLoader implements SpaceSystemLoader {
    int numPackets;
    int packetSize;
    int numParam;
    int paramSizeInBits;
    int numParamWithAlarmsPerPacket;
    DoubleRange warningRange, criticalRange;

    static String PACKET_ID_PARA_NAME = "packet-id";

    public PerfMdbLoader(YConfiguration config) {
        numPackets = config.getInt("numPackets");
        packetSize = config.getInt("packetSize");
        paramSizeInBits = config.getInt("paramSizeInBits", 32);
        if (paramSizeInBits > 64 || paramSizeInBits < 1) {
            throw new ConfigurationException("paramSizeInBits has to be between 1 and 64");
        }
        double percentangeParamWithAlarms = config.getDouble("percentangeParamWithAlarms", 5);
        if (percentangeParamWithAlarms < 0 || percentangeParamWithAlarms > 100) {
            throw new ConfigurationException("percentangeParamWithAlarms has to be between 0 and 100");
        }
        numParam = packetSize * 8 / paramSizeInBits;
        numParamWithAlarmsPerPacket = (int) (numParam * percentangeParamWithAlarms / 100);
        double warningOolChance = config.getDouble("warningOolChance", 1e-3);
        if (warningOolChance < 0 || warningOolChance > 1) {
            throw new ConfigurationException("warningOolChance has to be between 0 and 1");
        }
        double criticalOolChance = config.getDouble("criticalOolChance", 1e-5);
        if (criticalOolChance < 0 || criticalOolChance > 1) {
            throw new ConfigurationException("criticalOolChance has to be between 0 and 1");
        }

        double maxParmValue = Math.pow(2, paramSizeInBits) - 1;
        double warnMargin = maxParmValue * warningOolChance / 2;
        double criticalMargin = maxParmValue * criticalOolChance / 2;

        warningRange = new DoubleRange(config.getDouble("warningRangeMin", warnMargin),
                config.getDouble("warningRangeMax", maxParmValue - warnMargin));
        criticalRange = new DoubleRange(config.getDouble("criticalRangeMin", criticalMargin),
                config.getDouble("criticalRangeMax", maxParmValue - criticalMargin));
    }

    @Override
    public boolean needsUpdate(RandomAccessFile consistencyDateFile) throws IOException, ConfigurationException {
        return true;
    }

    @Override
    public String getConfigName() {
        return "perf-data";
    }

    @Override
    public void writeConsistencyDate(FileWriter consistencyDateFile) {
        return;
    }

    @Override
    public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
        SpaceSystem ss = new SpaceSystem("perf-data");

        // === Parameter Types ===

        // 32-bit int
        IntegerParameterType int32 = new IntegerParameterType.Builder()
                .setName("int32")
                .setSizeInBits(32)
                .setSigned(false)
                .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(32))
                .build();
        ss.addParameterType(int32);

        // 32-bit float
        FloatParameterType float32 = new FloatParameterType.Builder()
                .setName("float32")
                .setSizeInBits(32)
                .setEncoding(new FloatDataEncoding.Builder().setSizeInBits(32))
                .build();
        ss.addParameterType(float32);

        // 32-bit string (not used)
        // StringParameterType string32 = new StringParameterType.Builder()
        // .setName("string32")
        // .setEncoding(new StringDataEncoding.Builder().setSizeInBits(32))
        // .build();
        // ss.addParameterType(string32);

        // Boolean flags (1 bit each)
        BooleanParameterType boolType = new BooleanParameterType.Builder()
                .setName("bool1")
                .setEncoding(new BooleanDataEncoding.Builder().setSizeInBits(1))
                .build();
        ss.addParameterType(boolType);

        // 4-bit enum
        EnumeratedParameterType enum4 = new EnumeratedParameterType.Builder()
                .setName("enum4")
                .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(4))
                .addEnumerationValue(0, "OFF")
                .addEnumerationValue(1, "ON")
                .addEnumerationValue(2, "ERROR")
                .addEnumerationValue(3, "TEST")
                .build();
        ss.addParameterType(enum4);

        // 16-bit int w/ polynomial calibration (not polynomial not yet applied)
        // PolynomialCalibrator polyCal = new PolynomialCalibrator(new double[]{0.1, 1.2, -0.0005});
        IntegerParameterType int16cal = new IntegerParameterType.Builder()
                .setName("int16cal")
                .setSizeInBits(16)
                .setSigned(false)
                .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(16))
                .build();
        ss.addParameterType(int16cal);

        // === Build a Packet Container ===

        // Repeated pattern is:
        // 1 x int32
        // 1 x float32
        // 4 x bool1
        // 3 x enum4
        // 1 x int16
        // for a 96-bit cycle, 3x longer than original int32s

        for (int k = 0; k < numPackets; k++) {

            int pktId = PerfPacketGenerator.PERF_TEST_PACKET_ID + k;
            SequenceContainer sc = new SequenceContainer("pkt_" + pktId);
            sc.useAsArchivePartition(true);
            NameReference unr = new NameReference("/YSS/ccsds-default", Type.SEQUENCE_CONTAINER);

            unr.addResolvedAction(nd -> {
                addCcsdsInheritance((SequenceContainer) nd, sc, pktId);
            });
            ss.addUnresolvedReference(unr);

            int bitOffset = 0;
            Parameter p;
            for (int i = 0; i < numParam / 3; i++) { // divide by 3 because new recurring pattern is 3x longer

                p = makeParameter("int32_" + k + "_" + i, int32);
                ss.addParameter(p);
                sc.addEntry(makeEntry(p, bitOffset));
                bitOffset += 32;

                p = makeParameter("float32_" + k + "_" + i, float32);
                ss.addParameter(p);
                sc.addEntry(makeEntry(p, bitOffset));
                bitOffset += 32;

                // could not get strings to work, but that's problem for another day
                // p = makeParameter("string32_" + i, string32);
                // ss.addParameter(p);
                // sc.addEntry(makeEntry(p, bitOffset)); bitOffset += 32;

                for (int j = 0; j < 4; j++) {
                    p = makeParameter("bool_" + k + "_" + i + "_" + j, boolType);
                    ss.addParameter(p);
                    sc.addEntry(makeEntry(p, bitOffset));
                    bitOffset += 1;
                }

                for (int j = 0; j < 3; j++) {
                    p = makeParameter("enum4_" + k + "_" + i + "_" + j, enum4);
                    ss.addParameter(p);
                    sc.addEntry(makeEntry(p, bitOffset));
                    bitOffset += 4;
                }

                p = makeParameter("int16cal_" + k + "_" + i, int16cal);
                ss.addParameter(p);
                sc.addEntry(makeEntry(p, bitOffset));
                bitOffset += 16;
            }

            ss.addSequenceContainer(sc);
        }

        return ss;
    }

    private Parameter makeParameter(String name, ParameterType ptype) {
        Parameter p = new Parameter(name);
        p.setParameterType(ptype);
        return p;
    }

    private SequenceEntry makeEntry(Parameter p, int bitOffset) {
        ParameterEntry e = new ParameterEntry(0, ReferenceLocationType.PREVIOUS_ENTRY, p);
        return e;
    }

    private void addCcsdsInheritance(SequenceContainer ccsds, SequenceContainer sc, int id) {
        for (SequenceEntry se : ccsds.getEntryList()) {
            if (se instanceof ParameterEntry) {
                ParameterEntry pe = (ParameterEntry) se;
                if (PACKET_ID_PARA_NAME.equals(pe.getParameter().getName())) {
                    Parameter packetIdParam = pe.getParameter();
                    Comparison c = new Comparison(new ParameterInstanceRef(packetIdParam), Integer.toString(id),
                            OperatorType.EQUALITY);
                    sc.setBaseContainer(ccsds);
                    sc.setRestrictionCriteria(c);
                    return;
                }
            }
        }
        throw new ConfigurationException(
                "Cannot find a parameter '" + PACKET_ID_PARA_NAME + "' in the container " + ccsds.getName());
    }

}
