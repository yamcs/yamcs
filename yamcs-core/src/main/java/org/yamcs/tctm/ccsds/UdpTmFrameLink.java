package org.yamcs.tctm.ccsds;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.StringConverter;

/**
 * Receives telemetry frames via UDP. One UDP datagram = one TM frame.
 * 
 * 
 * @author nm
 *
 */
public class UdpTmFrameLink extends AbstractTmFrameLink implements Runnable {
    private volatile int invalidDatagramCount = 0;

    private DatagramSocket tmSocket;
    private int port;

    DatagramPacket datagram;

    String packetPreprocessorClassName;
    Object packetPreprocessorArgs;
    Thread thread;
    Boolean asmPresent; 

    /**
     * Creates a new UDP Frame Data Link
     * 
     * @throws ConfigurationException
     *             if port is not defined in the configuration
     */
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);
        port = config.getInt("port");
        int maxLength = frameHandler.getMaxFrameSize();
        datagram = new DatagramPacket(new byte[maxLength], maxLength);
    }

    @Override
    public void doStart() {
        if (!isDisabled()) {
            try {
                tmSocket = new DatagramSocket(port);
                new Thread(this).start();
            } catch (SocketException e) {
                notifyFailed(e);
            }
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        tmSocket.close();
        notifyStopped();
    }

    @Override
    public void run() {
        while (isRunningAndEnabled()) {
            try {

                // Detect if the Attached Synchro Marker (ASM) is present in the data link part of the yamcs.instance.yaml file
                asmPresent = config.getBoolean("asmPresent", false); // By default ASM is setted as false   
                
                // Array to select the first four bytes 
                byte[] firstsBytes = new byte[4];
                
                if (!asmPresent) {
                    tmSocket.receive(datagram);

                    // Select the first four bytes
                    for(int i = 0; i < 4; i++){
                        firstsBytes[i]=datagram.getData()[i];
                    }
                    String firstBytesStr = convertBytesToHexadecimal(firstsBytes);

                    if (firstBytesStr.equals("1ACFFC1D"))
                        throw new IllegalArgumentException("You specified your frame does not begin with the Attached Synchronization Marker but it seems it is...");

                    else { // If !asmPresent and the data indeed does not start with the ASM 
                        if (log.isTraceEnabled()) {
                            log.trace("Received datagram of length {}: {}", datagram.getLength(), StringConverter
                                    .arrayToHexString(datagram.getData(), datagram.getOffset(), datagram.getLength(), true));
                        }
                    }
                }

                else {
                    int maxLength = frameHandler.getMaxFrameSize();
                    DatagramPacket datagramWithAsm = new DatagramPacket(new byte[maxLength + 4 ], maxLength + 4);
                    datagram = new DatagramPacket(new byte[maxLength], maxLength);
                    tmSocket.receive(datagramWithAsm);
                    
                    // Select the first four bytes
                    for(int i = 0; i < 4; i++){
                        firstsBytes[i]=datagramWithAsm.getData()[i];
                    }
                    String firstBytesStr = convertBytesToHexadecimal(firstsBytes);
                    System.out.println("ASM: "+firstBytesStr);

                    if (!firstBytesStr.equals("1ACFFC1D")){
                        throw new IllegalArgumentException("You specified your frame begins with the Attached Synchronization Marker word but it is not.");
                    }

                    else{ // If asmPresent and the data indeed start with the ASM       

                        if (log.isTraceEnabled()) {
                            log.trace("Received datagram of length {}: {}", datagramWithAsm.getLength(), StringConverter
                                    .arrayToHexString(datagramWithAsm.getData(), datagramWithAsm.getOffset(), datagramWithAsm.getLength(), true));
                        }
                        datagram.setData(datagramWithAsm.getData(), datagramWithAsm.getOffset()+4, datagramWithAsm.getLength()-4);
                    }
                }

                handleFrame(timeService.getHresMissionTime(), datagram.getData(), datagram.getOffset(),
                        datagram.getLength());
            }

            catch (IOException e) {
                if (!isRunningAndEnabled()) {
                    break;
                }
                log.warn("exception {} thrown when reading from the UDP socket at port {}", port, e);
            } 
            catch (Exception e) {
                log.error("Error processing frame", e);
            }
        }
    
    }


    /**
     * returns statistics with the number of datagram received and the number of invalid datagrams
     */
    @Override
    public String getDetailedStatus() {
        if (isDisabled()) {
            return "DISABLED";
        } else {
            return String.format("OK (%s) %nValid datagrams received: %d%nInvalid datagrams received: %d",
                    port, frameCount.get(), invalidDatagramCount);
        }
    }

    @Override
    protected void doDisable() {
        if (tmSocket != null) {
            tmSocket.close();
            tmSocket = null;
        }
    }

    @Override
    protected void doEnable() throws SocketException {
        tmSocket = new DatagramSocket(port);
        new Thread(this).start();
    }

    @Override
    protected Status connectionStatus() {
        return Status.OK;
    }

    /**
     *  A parsing method used to compare the frame first words and the ASM as strings
     */
    public static String convertBytesToHexadecimal(byte[] byteArray)
    {
        String hex = "";
    
    // Iterating through each byte in the array
        for (byte i : byteArray) {
            hex += String.format("%02X", i);
        }
    
        return(hex);
    }
}
