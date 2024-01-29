package org.yamcs.tctm.pus.services.tm.three;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;

public class SubServiceTwentyFive implements PusSubService {
    String yamcsInstance;
    Log log;

    private int DEFAULT_SIMPLE_COMMUTATED_SIZE = 2;
    private int DEFAULT_SUPER_COMMUTATED_SAMPLE_REPETITION_NUMBER_SIZE = 2;
    private int DEFAULT_SIMPLE_COMMUTATIVE_DIFFERENTIATOR = 1;
    private int DEFAULT_SUPER_COMMUTATIVE_DIFFERENTIATOR = 2;
    private int DEFAULT_COLLECTION_INTERVAL_SIZE = 4;
    private int DEFAULT_HOUSEKEEPING_PARAMETER_REPORT_STRUCTURE_ID_SIZE = 4;

    private int simpleCommutatedSize;
    private int superCommutatedSampleRepetitionNumberSize;
    private int collectionIntervalSize;
    private int housekeepingParameterReportStructureIDSize;

    private byte simpleCommutativeDifferentiator;
    private byte superCommutativeDifferentiator;


    SubServiceTwentyFive(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        simpleCommutatedSize = config.getInt("simpleCommutatedSize", DEFAULT_SIMPLE_COMMUTATED_SIZE);
        superCommutatedSampleRepetitionNumberSize = config.getInt("superCommutatedSampleRepetitionNumberSize", DEFAULT_SUPER_COMMUTATED_SAMPLE_REPETITION_NUMBER_SIZE);
        collectionIntervalSize = config.getInt("collectionIntervalSize", DEFAULT_COLLECTION_INTERVAL_SIZE);
        housekeepingParameterReportStructureIDSize = config.getInt("housekeepingParameterReportStructureIDSize", DEFAULT_HOUSEKEEPING_PARAMETER_REPORT_STRUCTURE_ID_SIZE);

        simpleCommutativeDifferentiator = (byte) config.getInt("simpleCommutativeDifferentiator", DEFAULT_SIMPLE_COMMUTATIVE_DIFFERENTIATOR);
        superCommutativeDifferentiator = (byte) config.getInt("superCommutativeDifferentiator", DEFAULT_SUPER_COMMUTATIVE_DIFFERENTIATOR);
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    private TmPacket createSuperCommutativePusTmPacket(TmPacket tmPacket, byte[] housekeepingParameterReportStructureID, byte[] dataField, long gentime) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        
        ByteBuffer bb = ByteBuffer.allocate(
            pPkt.getPrimaryHeader().length + pPkt.getSecondaryHeader().length + housekeepingParameterReportStructureID.length + 1 + dataField.length
        );

        bb.put(pPkt.getPrimaryHeader());
        bb.put(pPkt.getSecondaryHeader());
        bb.put(housekeepingParameterReportStructureID);
        bb.put(superCommutativeDifferentiator);
        bb.put(dataField);

        TmPacket newPkt = new TmPacket(tmPacket.getReceptionTime(), gentime,
            tmPacket.getSeqCount(), bb.array());
        newPkt.setEarthReceptionTime(tmPacket.getEarthReceptionTime());

        return newPkt;
    }

    private TmPacket createSimpleCommutativePusTmPacket(TmPacket tmPacket, byte[] housekeepingParameterReportStructureID, byte[] dataField) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());
        
        ByteBuffer bb = ByteBuffer.allocate(
            pPkt.getPrimaryHeader().length + pPkt.getSecondaryHeader().length + housekeepingParameterReportStructureID.length + 1 + dataField.length
        );

        bb.put(pPkt.getPrimaryHeader());
        bb.put(pPkt.getSecondaryHeader());
        bb.put(housekeepingParameterReportStructureID);
        bb.put(simpleCommutativeDifferentiator);
        bb.put(dataField);

        TmPacket newPkt = new TmPacket(tmPacket.getReceptionTime(), tmPacket.getGenerationTime(),
            tmPacket.getSeqCount(), bb.array());
        newPkt.setEarthReceptionTime(tmPacket.getEarthReceptionTime());

        return newPkt;
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        ArrayList<TmPacket> pPkts = new ArrayList<>();
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());

        byte[] spareField = pPkt.getSpareField();
        byte[] dataField = pPkt.getDataField();

        int simpleCommutatedLength = (int) ByteArrayUtils.decodeCustomInteger(spareField, 0, simpleCommutatedSize);
        int superCommutatedSampleRepetitionNumber = (int) ByteArrayUtils.decodeCustomInteger(spareField, simpleCommutatedSize, superCommutatedSampleRepetitionNumberSize);
        int collectionInterval = (int) ByteArrayUtils.decodeCustomInteger(spareField, (simpleCommutatedSize + superCommutatedSampleRepetitionNumberSize), collectionIntervalSize);

        byte[] housekeepingParameterReportStructureID = Arrays.copyOfRange(
            dataField,
            0,
            housekeepingParameterReportStructureIDSize
        );
        byte[] simpleCommutatedParameterArray = Arrays.copyOfRange(
            dataField,
            housekeepingParameterReportStructureIDSize,
            (housekeepingParameterReportStructureIDSize + simpleCommutatedLength)
        );
        pPkts.add(
            createSimpleCommutativePusTmPacket(tmPacket, housekeepingParameterReportStructureID, simpleCommutatedParameterArray)
        );

        if (superCommutatedSampleRepetitionNumber != 0) {
            byte[] superCommutedParameterArray = Arrays.copyOfRange(dataField, (housekeepingParameterReportStructureIDSize + simpleCommutatedLength), dataField.length);
            int superCommutatedParameterSubStructureSize = (int) superCommutedParameterArray.length / superCommutatedSampleRepetitionNumber; // FIXME: No need to typecast, since it will always be perfectly divisbible

            long gentime = tmPacket.getGenerationTime();
            long collectionIntervalOffset = collectionInterval / superCommutatedSampleRepetitionNumber;

            for (int index = 0; index < superCommutatedSampleRepetitionNumber; index++) {
                byte[] superCommutativeParameterSubStructure = Arrays.copyOfRange(
                    superCommutedParameterArray,
                    index * superCommutatedParameterSubStructureSize,
                    (index + 1) * superCommutatedParameterSubStructureSize
                );
                pPkts.add(createSuperCommutativePusTmPacket(
                    tmPacket,
                    housekeepingParameterReportStructureID,
                    superCommutativeParameterSubStructure,
                    gentime - (index * collectionIntervalOffset)    // Sampling time of parameters comes packet generation time
                ));
            }
        }

        return pPkts;
    }
}
