package org.yamcs.tctm.ccsds;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.InputStream;
import java.util.Arrays;


import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.CcsdsPacketInputStream;
import org.yamcs.tctm.PacketInputStream;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.YObjectLoader;


/**
 * Receives telemetry frames via TCP.
 * 
 * 
 * @author jp and a.laborie
 *
 */
public class TcpTmFrameLink extends AbstractTmFrameLink implements Runnable {
    private volatile int invalidDatagramCount = 0;

    private Socket tmSocket;
    private String host;
    private int port;
    protected long initialDelay;

    String packetPreprocessorClassName;
    Object packetPreprocessorArgs;
    Thread thread;
    Boolean asmPresent; 
    byte[] asm; 
    int asmLength;

    String packetInputStreamClassName;
    YConfiguration packetInputStreamArgs;
    PacketInputStream packetInputStream;

    /**
     * Creates a new TCP Frame Data Link
     * 
     * @throws ConfigurationException
     *             if port is not defined in the configuration
     */

    /**
     * Creates a new TCP Frame Data Link
     * 
     * @throws ConfigurationException
     *             if port is not defined in the configuration
     */
    public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
        super.init(instance, name, config);
        host = config.getString("host");
        port = config.getInt("port");
        initialDelay = config.getLong("initialDelay", -1);
        // Detect if the Attached Synchro Marker (ASM) is present in the data link part of the yamcs.instance.yaml file
        asmPresent = config.getBoolean("asmPresent", false); // By default ASM is absent 
        asmLength=0;
        if(asmPresent){
            asm = hexStringToByteArray("1ACFFC1D");
            asmLength=4;
        }

        if (config.containsKey("packetInputStreamClassName")) {
            this.packetInputStreamClassName = config.getString("packetInputStreamClassName");
            this.packetInputStreamArgs = config.getConfig("packetInputStreamArgs");
        } else {
            this.packetInputStreamClassName = CcsdsPacketInputStream.class.getName();
            this.packetInputStreamArgs = YConfiguration.emptyConfig();
        }
    
    }

    protected void openSocket() throws IOException {
        InetAddress address = InetAddress.getByName(host);
        tmSocket = new Socket();
        tmSocket.setKeepAlive(true);
        tmSocket.connect(new InetSocketAddress(address, port), 1000);
        try {
            packetInputStream = YObjectLoader.loadObject(packetInputStreamClassName);
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packetInput stream", e);
            throw e;
        }
        packetInputStream.init(tmSocket.getInputStream(), packetInputStreamArgs);
    } 

    @Override
    public void doStart() {
        if (!isDisabled()) {
            doEnable();
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        if (thread != null) {
            thread.interrupt();
        }
        if (tmSocket != null) {
            try {
                tmSocket.close();
            } catch (IOException e) {
                log.warn("Exception got when closing the TM socket:", e);
            }
            tmSocket = null;
        }
        notifyStopped();
    }

    @Override
    public void run() {

        if (initialDelay > 0) {
            try {
                Thread.sleep(initialDelay);
                initialDelay = -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        while (isRunningAndEnabled()) {
            int maxLength = frameHandler.getMaxFrameSize();

            try {
                if (tmSocket == null) {
                    openSocket();
                    log.info("Link established to {}:{}", host, port);
                }

                InputStream stream = tmSocket.getInputStream();
                byte[] data = new byte[maxLength + asmLength];
                int dataLength= 0; 

                //data offset is hardcoded there
                int offset = 0;
                
                
                // Detect if the Attached Synchro Marker (ASM) is present in the data link part of the yamcs.instance.yaml file
                asmPresent = config.getBoolean("asmPresent", false); // By default ASM is setted as false   
                // Array to select the first four bytes 
                byte[] firstBytes = new byte[4];

                if (!asmPresent) {

                    dataLength= stream.read(data);
                    // If the stream is empty, we need to throw an exception
                    if (dataLength== -1){
                        throw new IOException();
                    }

                    // Select the first four bytes
                    for(int i = 0; i < 4; i++){
                        firstBytes[i]=data[i];
                    }

                    if (Arrays.equals(firstBytes, asm))
                        log.warn("Yaml configuration specifies frames do not begin with the Attached Synchronization Marker but it seems there are...");


                    else { // If !asmPresent and the data indeed does not start with the ASM 
                        if (log.isTraceEnabled()) {
                            log.trace("Received datagram of length {}: {}", data.length, StringConverter
                                    .arrayToHexString(data, 0, data.length, true));
                        }
                    } 
                }
                
                else {
                    dataLength = stream.read(data);
                    // If the stream is empty, we need to throw an exception
                    if (dataLength== -1){
                        throw new IOException();
                    }
                    
                    // Select the first four bytes
                    for(int i = 0; i < 4; i++){
                        firstBytes[i]=data[i];
                    }

                    if (!Arrays.equals(firstBytes, asm)){
                        throw new IllegalArgumentException("You specified your frame begins with the Attached Synchronization Marker word but it is not.");
                    }
                }

                handleFrame(timeService.getHresMissionTime(), data, offset + asmLength, dataLength - asmLength);


            } catch (IOException e) {
                if (!isRunningAndEnabled()) {
                    break;
                }
                if (isRunningAndEnabled()) {
                    String msg;
                    if (e instanceof EOFException) {
                        msg = "TM socket connection to " + host + ":" + port + " closed. Reconnecting in 10s.";
                    } else {
                        msg = "Cannot open or read TM socket " + host + ": " + port + ": "
                                + ((e instanceof ConnectException) ? e.getMessage() : e.toString())
                                + ". Retrying in 10 seconds.";
                    }
                    log.warn(msg);
                }
                forceClosedSocket();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
            } catch (Exception e) {
                log.error("Error processing frame", e);
            }
        }
    }

    @Override
    protected void doDisable() throws InterruptedException {
        if (tmSocket != null) {
            try {
                tmSocket.close();  
            } catch (IOException e) {
                log.warn("Exception got when closing the TM TCP socket:", e);
            }
            tmSocket = null;
        }
        if (thread != null) {
            thread.interrupt();
        }   
    }

    @Override
    public void doEnable() {
        thread = new Thread(this);
        thread.setName(this.getClass().getSimpleName() + "-" + linkName);
        thread.start();
    }

    /**
     * returns statistics with the number of datagram received and the number of invalid datagrams
     */
    @Override
    public String getDetailedStatus() {
        if (isDisabled()) {
            return String.format("DISABLED (should connect to %s:%d)", host, port);
        }
        if (tmSocket == null) {
            return String.format("Not connected to %s:%d", host, port);
        } {
            return String.format("OK (%s) %nValid packets received: %d%nInvalid packets received: %d",
                    port, frameCount.get(), invalidDatagramCount);
        }
    }
    @Override
    protected Status connectionStatus() {
        return (tmSocket == null) ? Status.UNAVAIL : Status.OK;
    }

    private void forceClosedSocket() {
        if (tmSocket != null) {
            try {
                tmSocket.close();
            } catch (Exception e2) {
            }
        }
        tmSocket = null;
    }

    /**
     *  A parsing method used to initialize the ASM from a string value
     *  s must be an even-length string.
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
