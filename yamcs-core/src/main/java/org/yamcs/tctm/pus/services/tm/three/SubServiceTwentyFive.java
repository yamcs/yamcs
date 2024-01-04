package org.yamcs.tctm.pus.services.tm.three;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.logging.Log;
import org.yamcs.tctm.TmPackage;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmModifier;

public class SubServiceTwentyFive implements PusSubService {
    String yamcsInstance;
    Log log;

    private int DEFAULT_SIMPLE_COMMUTATED_SIZE = 2;
    private int DEFAULT_SUPER_COMMUTATED_SAMPLE_REPETITION_NUMBER_SIZE = 2;
    private int DEFAULT_SIMPLE_COMMUTATIVE_DIFFERENTIATOR = 1;
    private int DEFAULT_SUPER_COMMUTATIVE_DIFFERENTIATOR = 2;
    private int DEFAULT_COLLECTION_INTERVAL_SIZE = 4;

    private int simpleCommutatedSize;
    private int superCommutatedSampleRepetitionNumberSize;
    private int collectionIntervalSize;
    private byte simpleCommutativeDifferentiator;
    private byte superCommutativeDifferentiator;


    SubServiceTwentyFive(String yamcsInstance, YConfiguration config) {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);

        simpleCommutatedSize = config.getInt("simpleCommutatedSize", DEFAULT_SIMPLE_COMMUTATED_SIZE);
        superCommutatedSampleRepetitionNumberSize = config.getInt("superCommutatedSampleRepetitionNumberSize", DEFAULT_SUPER_COMMUTATED_SAMPLE_REPETITION_NUMBER_SIZE);
        collectionIntervalSize = config.getInt("collectionIntervalSize", DEFAULT_COLLECTION_INTERVAL_SIZE);

        simpleCommutativeDifferentiator = (byte) config.getInt("simpleCommutativeDifferentiator", DEFAULT_SIMPLE_COMMUTATIVE_DIFFERENTIATOR);
        superCommutativeDifferentiator = (byte) config.getInt("superCommutativeDifferentiator", DEFAULT_SUPER_COMMUTATIVE_DIFFERENTIATOR);
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

    private void createSuperCommutativePusTmContainer(TmPacket tmPacket, byte[] housekeepingParameterReportStructureID, byte[] dataField, long gentimeOffset) throws IOException {
        long gentime = tmPacket.getGenerationTime() + gentimeOffset;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(PusTmModifier.getPrimaryHeader(tmPacket));
        outputStream.write(PusTmModifier.getSecondaryHeader(tmPacket));
        outputStream.write(housekeepingParameterReportStructureID);
        outputStream.write(superCommutativeDifferentiator);
        outputStream.write(dataField);

        addPusTmContainer(tmPacket, outputStream.toByteArray(), gentime);
    }

    private void createSimpleCommutativePusTmContainer(TmPacket tmPacket, byte[] housekeepingParameterReportStructureID, byte[] dataField) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(PusTmModifier.getPrimaryHeader(tmPacket));
        outputStream.write(PusTmModifier.getSecondaryHeader(tmPacket));
        outputStream.write(housekeepingParameterReportStructureID);
        outputStream.write(simpleCommutativeDifferentiator);
        outputStream.write(dataField);

        addPusTmContainer(tmPacket, outputStream.toByteArray(), tmPacket.getGenerationTime());
    }

    private void addPusTmContainer(TmPacket tmPacket, byte[] pusTmContainer, long gentime) {
        tmPacket.addToTmPackageList(new TmPackage(pusTmContainer, gentime));
    }

    @Override
    public TmPacket process(TmPacket tmPacket) {
        byte[] spareField = PusTmModifier.getSpareField(tmPacket);
        byte[] dataField = PusTmModifier.getDataField(tmPacket);

        int simpleCommutatedLength = PusTmModifier.decodeByteArrayToInteger(spareField, simpleCommutatedSize, 0);
        int superCommutatedSampleRepetitionNumber = PusTmModifier.decodeByteArrayToInteger(spareField, superCommutatedSampleRepetitionNumberSize, simpleCommutatedSize);
        int collectionInterval = PusTmModifier.decodeByteArrayToInteger(spareField, collectionIntervalSize, (simpleCommutatedSize + superCommutatedSampleRepetitionNumberSize));

        int housekeepingParameterReportStructureIDSize = 4;
        byte[] housekeepingParameterReportStructureID = Arrays.copyOfRange(dataField, 0, housekeepingParameterReportStructureIDSize);

        try {
            byte[] simpleCommutatedParameterArray = Arrays.copyOfRange(dataField, housekeepingParameterReportStructureIDSize, (housekeepingParameterReportStructureIDSize + simpleCommutatedLength));
            createSimpleCommutativePusTmContainer(tmPacket, housekeepingParameterReportStructureID, simpleCommutatedParameterArray);

        } catch (IOException e) {
            // FIXME: Should not be thrown
        }

        byte[] superCommutedParameterArray = Arrays.copyOfRange(dataField, (housekeepingParameterReportStructureIDSize + simpleCommutatedLength), dataField.length);
        int superCommutatedParameterSubStructureSize = (int) superCommutedParameterArray.length / superCommutatedSampleRepetitionNumber; // No need to typecast, since it will always be perfectly divisbible

        for(int index = 0; index < superCommutatedSampleRepetitionNumber; index++) {            
            try {
                byte[] superCommutativeParameterSubStructure = Arrays.copyOfRange(superCommutedParameterArray, index * superCommutatedParameterSubStructureSize, (index + 1) * superCommutatedParameterSubStructureSize);
                createSuperCommutativePusTmContainer(tmPacket, housekeepingParameterReportStructureID, superCommutativeParameterSubStructure, (long) collectionInterval / superCommutatedSampleRepetitionNumber);

            } catch (IOException e) {
                // FIXME: Should not happen
            }
        }

        return tmPacket;
    }
    
}
