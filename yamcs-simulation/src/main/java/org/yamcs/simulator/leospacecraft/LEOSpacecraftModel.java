package org.yamcs.simulator.leospacecraft;

import java.nio.ByteBuffer;

import org.yamcs.simulator.CCSDSPacket;
import org.yamcs.simulator.SimulationData;
import org.yamcs.simulator.SimulationModel;

/**
 * Cheap-o model. A very thin layer on top of the CSV, really
 */
public class LEOSpacecraftModel implements SimulationModel {
    
    private static final int PACKET_ID = 1;
    private static final int PAYLOAD_SIZE = (42*32) / 8; // Bytes
    
    private static final String EPOCH_USNO = "LEO_Spacecraft.EpochUSNO";
    private static final String ORBIT_NUMBER_CUMULATIVE = "LEO_Spacecraft.OrbitNumberCumulative";
    private static final String ELAPSED_SECONDS = "LEO_Spacecraft.ElapsedSeconds";
    private static final String A = "LEO_Spacecraft.A";
    private static final String HEIGHT = "LEO_Spacecraft.Height";
    private static final String X = "LEO_Spacecraft.X";
    private static final String Y = "LEO_Spacecraft.Y";
    private static final String Z = "LEO_Spacecraft.Z";
    private static final String VX = "LEO_Spacecraft.VX";
    private static final String VY = "LEO_Spacecraft.VY";
    private static final String VZ = "LEO_Spacecraft.VZ";
    private static final String SHADOW = "LEO_Spacecraft.Shadow";
    private static final String CONTACT_GOLBASI_GS = "LEO_Spacecraft.Contact(Golbasi_GS)";
    private static final String CONTACT_SVALBARD = "LEO_Spacecraft.Contact(Svalbard)";
    private static final String LATITUDE = "LEO_Spacecraft.Latitude";
    private static final String LONGITUDE = "LEO_Spacecraft.Longitude";
    private static final String PAYLOAD_STATUS = "Paylaod_Status";
    private static final String PAYLOAD_ERROR_FLAG = "payload_error_flag";
    private static final String ADCS_ERROR_FLAG = "ADCS_error_flag";
    private static final String CDHS_ERROR_FLAG = "CDHS_error_flag";
    private static final String COMMS_ERROR_FLAG = "COMMS_error_flag";
    private static final String EPS_ERROR_FLAG = "EPS_error_flag";
    private static final String COMMS_STATUS = "COMMS_status";
    private static final String CDHS_STATUS = "CDHS_status";
    private static final String BATTERY1_VOLTAGE = "Bat1_voltage";
    private static final String BATTERY2_VOLTAGE = "Bat2_voltage";
    private static final String BATTERY1_TEMP = "bat1_temp";
    private static final String BATTERY2_TEMP = "bat2_temp";
    private static final String MAGNETOMETER_X = "Magnetometer_X";
    private static final String MAGNETOMETER_Y = "Magnetometer_Y";
    private static final String MAGNETOMETER_Z = "Magnetometer_Z";
    private static final String SUNSENSOR = "Sunsensor";
    private static final String GYRO_X = "Gyro_X";
    private static final String GYRO_Y = "Gyro_Y";
    private static final String GYRO_Z = "Gyro_Z";
    private static final String DETECTOR_TEMP = "detector_temp";
    private static final String MODE_NIGHT = "mode_night";
    private static final String MODE_DAY = "mode_day";
    private static final String MODE_PAYLOAD = "mode_payload";
    private static final String MODE_XBAND = "mode_xband";
    private static final String MODE_SBAND = "mode_sband";
    private static final String MODE_SAFE = "mode_safe";
    
    private SimulationData latestData;
    
    private boolean battery1ForceOff = false;
    private boolean battery2ForceOff = false;
    
    @Override
    public void step(long t, SimulationData simulationData) {
        latestData = simulationData;
    }
    
    public void forceBatteryOneOff(boolean off) {
        battery1ForceOff = off;
    }
    
    public void forceBatteryTwoOff(boolean off) {
        battery2ForceOff = off;
    }

    @Override
    public CCSDSPacket toCCSDSPacket() {
        CCSDSPacket packet = new CCSDSPacket(PAYLOAD_SIZE, PACKET_ID);
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.putFloat(latestData.getFloat(EPOCH_USNO));
        buffer.putInt(latestData.getInt(ORBIT_NUMBER_CUMULATIVE));
        buffer.putInt(latestData.getInt(ELAPSED_SECONDS));
        buffer.putFloat(latestData.getFloat(A));
        buffer.putFloat(latestData.getFloat(HEIGHT));
        buffer.putFloat(latestData.getFloat(X));
        buffer.putFloat(latestData.getFloat(Y));
        buffer.putFloat(latestData.getFloat(Z));
        buffer.putFloat(latestData.getFloat(VX));
        buffer.putFloat(latestData.getFloat(VY));
        buffer.putFloat(latestData.getFloat(VZ));
        buffer.putInt(latestData.getInt(SHADOW));
        buffer.putInt(latestData.getInt(CONTACT_GOLBASI_GS));
        buffer.putInt(latestData.getInt(CONTACT_SVALBARD));
        buffer.putFloat(latestData.getFloat(LATITUDE));
        buffer.putFloat(latestData.getFloat(LONGITUDE));
        buffer.putInt(latestData.getInt(PAYLOAD_STATUS));
        buffer.putInt(latestData.getInt(PAYLOAD_ERROR_FLAG));
        buffer.putInt(latestData.getInt(ADCS_ERROR_FLAG));
        buffer.putInt(latestData.getInt(CDHS_ERROR_FLAG)); 
        buffer.putInt(latestData.getInt(COMMS_ERROR_FLAG));
        buffer.putInt(latestData.getInt(EPS_ERROR_FLAG));
        buffer.putInt(latestData.getInt(COMMS_STATUS));
        buffer.putInt(latestData.getInt(CDHS_STATUS));
        
        if (battery1ForceOff)
            buffer.putFloat(0);
        else 
            buffer.putFloat(latestData.getFloat(BATTERY1_VOLTAGE));
        
        if (battery2ForceOff)
            buffer.putFloat(0);
        else
            buffer.putFloat(latestData.getFloat(BATTERY2_VOLTAGE));
        
        buffer.putFloat(latestData.getFloat(BATTERY1_TEMP));
        buffer.putFloat(latestData.getFloat(BATTERY2_TEMP));
        buffer.putFloat(latestData.getFloat(MAGNETOMETER_X));
        buffer.putFloat(latestData.getFloat(MAGNETOMETER_Y));
        buffer.putFloat(latestData.getFloat(MAGNETOMETER_Z));
        buffer.putFloat(latestData.getFloat(SUNSENSOR));
        buffer.putFloat(latestData.getFloat(GYRO_X));
        buffer.putFloat(latestData.getFloat(GYRO_Y));
        buffer.putFloat(latestData.getFloat(GYRO_Z));
        buffer.putFloat(latestData.getFloat(DETECTOR_TEMP));
        buffer.putInt(latestData.getInt(MODE_NIGHT));
        buffer.putInt(latestData.getInt(MODE_DAY));
        buffer.putInt(latestData.getInt(MODE_PAYLOAD));
        buffer.putInt(latestData.getInt(MODE_XBAND));
        buffer.putInt(latestData.getInt(MODE_SBAND));
        buffer.putInt(latestData.getInt(MODE_SAFE));
        return packet;
    }
}
