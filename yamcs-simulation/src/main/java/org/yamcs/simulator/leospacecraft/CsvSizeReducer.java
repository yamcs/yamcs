package org.yamcs.simulator.leospacecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Cleans and reduces filesize of HKM's data file by converting float-encoded
 * to ints after gz compression, difference is only about 2mb though. 
 * <p>
 * Keeping this around for when we need to do something similar again
 */
public class CsvSizeReducer {
    
    public static void main(String... args) throws IOException {
        try (FileWriter writer = new FileWriter(new File("/Users/fdi/satellite.csv"));
                BufferedReader in = new BufferedReader(new FileReader("/Users/fdi/rep1.txt"))) {
            in.readLine();
            
            // Headers
            String line = in.readLine();
            String[] titles = line.split("\\s+");
            for (int i=1; i<titles.length; i++) {
                writer.write(titles[i]);
                if (i < titles.length - 1) {
                    writer.write(",");
                }
            }
            writer.write("\n");
            
            // Units
            in.readLine();
            
            // Data
            while ((line = in.readLine()) != null) {
                String[] parts = line.split("\\s+");
                writer.write(parts[1]); // LEO_Spacecraft.EpochUSNO;
                writer.write(',');
                writer.write(asInt(parts[2])); // LEO_Spacecraft.OrbitNumberCumulative;
                writer.write(',');
                writer.write(asInt(parts[3])); // LEO_Spacecraft.ElapsedSeconds;
                writer.write(',');
                writer.write(parts[4]); // LEO_Spacecraft.A;
                writer.write(',');
                writer.write(parts[5]); // LEO_Spacecraft.Height;
                writer.write(',');
                writer.write(parts[6]); // LEO_Spacecraft.X;
                writer.write(',');
                writer.write(parts[7]); // LEO_Spacecraft.Y;
                writer.write(',');
                writer.write(parts[8]); // LEO_Spacecraft.Z;
                writer.write(',');
                writer.write(parts[9]); // LEO_Spacecraft.VX;
                writer.write(',');
                writer.write(parts[10]); // LEO_Spacecraft.VY;
                writer.write(',');
                writer.write(parts[11]); // LEO_Spacecraft.VZ;
                writer.write(',');
                writer.write(asInt(parts[12])); // LEO_Spacecraft.Shadow;
                writer.write(',');
                writer.write(asInt(parts[13])); // LEO_Spacecraft.Contact(Golbasi_GS); 
                writer.write(',');
                writer.write(asInt(parts[14])); // LEO_Spacecraft.Contact(Svalbard); 
                writer.write(',');
                writer.write(parts[15]); // LEO_Spacecraft.Latitude; 
                writer.write(',');
                writer.write(parts[16]); // LEO_Spacecraft.Longitude; 
                writer.write(',');
                writer.write(asInt(parts[17])); // Payload_Status;
                writer.write(',');
                writer.write(asInt(parts[18])); // payload_error_flag;
                writer.write(',');
                writer.write(asInt(parts[19])); // ADCS_error_flag;
                writer.write(',');
                writer.write(asInt(parts[20])); // CDHS_error_flag; 
                writer.write(',');
                writer.write(asInt(parts[21])); // COMMS_error_flag; 
                writer.write(',');
                writer.write(asInt(parts[22])); // EPS_error_flag;
                writer.write(',');
                writer.write(asInt(parts[23])); // COMMS_status; 
                writer.write(',');
                writer.write(asInt(parts[24])); // CDHS_status;
                writer.write(',');
                writer.write(parts[25]); // Bat1_voltage; 
                writer.write(',');
                writer.write(parts[26]); // Bat2_voltage;
                writer.write(',');
                writer.write(parts[27]); // bat1_temp; 
                writer.write(',');
                writer.write(parts[28]); // bat2_temp;
                writer.write(',');
                writer.write(parts[29]); // Magnetometer_X; 
                writer.write(',');
                writer.write(parts[30]); // Magnetometer_Y;
                writer.write(',');
                writer.write(parts[31]); // Magnetometer_Z; 
                writer.write(',');
                writer.write(parts[32]); // Sunsensor;
                writer.write(',');
                writer.write(parts[33]); // Gyro_X;
                writer.write(',');
                writer.write(parts[34]); // Gyro_Y;
                writer.write(',');
                writer.write(parts[35]); // Gyro_Z;
                writer.write(',');
                writer.write(parts[36]); // detector_temp;
                writer.write(',');
                writer.write(asInt(parts[37])); // mode_night; 
                writer.write(',');
                writer.write(asInt(parts[38])); // mode_day;
                writer.write(',');
                writer.write(asInt(parts[39])); // mode_payload; 
                writer.write(',');
                writer.write(asInt(parts[40])); // mode_xband;
                writer.write(',');
                writer.write(asInt(parts[41])); // mode_sband;
                writer.write(',');
                writer.write(asInt(parts[42])); // mode_safe;
                writer.write("\n");
            }
        }
    }
    
    private static String asInt(String part) {
        return Integer.valueOf(Float.valueOf(part).intValue()).toString();
    }
}
