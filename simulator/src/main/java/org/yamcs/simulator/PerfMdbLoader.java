package org.yamcs.simulator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.mdb.SpaceSystemLoader;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.util.NameReference.Type;
import org.yamcs.xtce.util.DoubleRange;
import org.yamcs.xtce.util.NameReference;
import org.yamcs.xtce.SpaceSystem;

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
        IntegerParameterType.Builder ptypeb = new IntegerParameterType.Builder().setName("uint" + paramSizeInBits);
        ptypeb.setSizeInBits(paramSizeInBits);
        ptypeb.setSigned(false);
        IntegerDataEncoding.Builder ide = new IntegerDataEncoding.Builder().setSizeInBits(paramSizeInBits);
        ptypeb.setEncoding(ide);
        IntegerParameterType basicIntType = ptypeb.build();
        ss.addParameterType(basicIntType);


        for (int j = 0; j < numPackets; j++) {
            int numAlarms = 0;
            int pktId = PerfPacketGenerator.PERF_TEST_PACKET_ID + j;
            SequenceContainer sc = new SequenceContainer("pkt_" + pktId);
            sc.useAsArchivePartition(true);
            NameReference unr = new NameReference("/YSS/ccsds-default", Type.SEQUENCE_CONTAINER);

            unr.addResolvedAction(nd -> {
                addCcsdsInheritance((SequenceContainer) nd, sc, pktId);
            });
            ss.addUnresolvedReference(unr);
            for (int i = 0; i < numParam; i++) {
                IntegerParameterType ptype;
                Parameter p = new Parameter("p_" + pktId + "_" + basicIntType.getName() + "_" + i);
                if (numAlarms < numParamWithAlarmsPerPacket) {
                    ptypeb = new IntegerParameterType.Builder(basicIntType);
                    ptypeb.setName("uint" + paramSizeInBits + "_" + j + "_" + i);
                    ptypeb.addWarningAlarmRange(null, warningRange);
                    ptypeb.addCriticalAlarmRange(null, criticalRange);
                    ptype = ptypeb.build();
                    ss.addParameterType(ptype);
                    numAlarms++;
                } else {
                    ptype = basicIntType;
                }

                p.setParameterType(ptype);
                ParameterEntry pe = new ParameterEntry(128 + paramSizeInBits * i, ReferenceLocationType.CONTAINER_START,
                        p);
                sc.addEntry(pe);
                ss.addParameter(p);

            }
            ss.addSequenceContainer(sc);
        }
        return ss;
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
