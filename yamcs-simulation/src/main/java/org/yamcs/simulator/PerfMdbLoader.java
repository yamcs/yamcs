package org.yamcs.simulator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.simulation.simulator.PerfPacketGenerator;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.DatabaseLoadException;
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
import org.yamcs.xtce.util.UnresolvedNameReference;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SpaceSystemLoader;

/**
 * Generates a MDB used for performance testing.
 * 
 * For the moment it generates only int32 parameters
 * 
 * @author nm
 *
 */
public class PerfMdbLoader implements SpaceSystemLoader {
    int numPackets;
    int packetSize;
    int numParam;
    static String PACKET_ID_PARA_NAME = "packet-id";

    public PerfMdbLoader(Map<String, Object> config) {
        numPackets = YConfiguration.getInt(config, "numPackets");
        packetSize = YConfiguration.getInt(config, "packetSize");
        numParam = packetSize / 4;
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
        IntegerParameterType uint32 = new IntegerParameterType("uint32");
        uint32.setSizeInBits(32);
        IntegerDataEncoding ide = new IntegerDataEncoding(32);
        uint32.setEncoding(ide);
        ss.addParameterType(uint32);
        for (int j = 0; j < numPackets; j++) {
            int pktId = PerfPacketGenerator.PERF_TEST_PACKET_ID + j;
            SequenceContainer sc = new SequenceContainer("pkt_" + pktId);
            UnresolvedNameReference unr = new UnresolvedNameReference("/YSS/ccsds-default", Type.SEQUENCE_CONTAINER);

            unr.addResolvedAction(nd -> {
                addCcsdsInheritance((SequenceContainer) nd, sc, pktId);
                return true;
            });
            ss.addUnresolvedReference(unr);
            for (int i = 0; i < numParam; i++) {
                Parameter p = new Parameter("p_"+pktId+"_uint_" + i);
                p.setParameterType(uint32);
                ParameterEntry pe = new ParameterEntry(128 + 32 * i, ReferenceLocationType.containerStart, p);
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
                    Comparison c = new Comparison(new ParameterInstanceRef(packetIdParam), id, OperatorType.EQUALITY);
                    sc.setBaseContainer(ccsds);
                    sc.setRestrictionCriteria(c);
                    return;
                }
            }
        }
        throw new ConfigurationException("Cannot find a parameter '"+PACKET_ID_PARA_NAME+"' in the container " + ccsds.getName());
    }

}
