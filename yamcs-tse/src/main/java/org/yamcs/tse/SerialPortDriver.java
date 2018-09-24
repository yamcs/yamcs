package org.yamcs.tse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.yamcs.YConfiguration;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Connect and command a device over a serial port.
 * 
 * Not thread safe.
 */
public class SerialPortDriver extends InstrumentDriver {

    private static final int POLLING_INTERVAL = 20;

    private static SerialPort link;

    private String devicePath;
    private int baudrate = 9600;
    private int dataBits = 8;
    private String parity;

    public SerialPortDriver(String name, Map<String, Object> args) {
        super(name, args);
        this.devicePath = YConfiguration.getString(args, "path");

        if (args.containsKey("baudrate")) {
            baudrate = YConfiguration.getInt(args, "baudrate");
        }
        if (args.containsKey("dataBits")) {
            dataBits = YConfiguration.getInt(args, "dataBits");
        }
        if (args.containsKey("parity")) {
            parity = YConfiguration.getString(args, "parity");
        }
    }

    public int getBaudrate() {
        return baudrate;
    }

    public int getDataBits() {
        return dataBits;
    }

    public String getParity() {
        return parity;
    }

    public String getPath() {
        return devicePath;
    }

    @Override
    public void connect() {
        if (link != null && link.isOpen()) {
            return;
        }

        link = SerialPort.getCommPort(devicePath);
        link.setBaudRate(baudrate);
        link.setNumDataBits(dataBits);

        if ("odd".equals(parity)) {
            link.setParity(SerialPort.ODD_PARITY);
        } else if ("even".equals(parity)) {
            link.setParity(SerialPort.EVEN_PARITY);
        } else {
            link.setParity(SerialPort.NO_PARITY);
        }

        link.openPort();
        link.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
    }

    @Override
    public void write(String cmd) {
        byte[] bytes = cmd.getBytes();
        link.writeBytes(bytes, bytes.length);
    }

    @Override
    public String read() throws IOException, TimeoutException {
        long time = System.currentTimeMillis();
        long timeoutTime = time + responseTimeout;

        ResponseBuilder responseBuilder = new ResponseBuilder(encoding, getResponseTermination());
        try {
            while (System.currentTimeMillis() < timeoutTime) {
                int n = link.bytesAvailable();
                if (n > 0) {
                    byte[] buf = new byte[n];
                    link.readBytes(buf, n);
                    responseBuilder.append(buf, 0, n);
                    String response = responseBuilder.parseCompleteResponse();
                    if (response != null) {
                        return response;
                    }
                }
                Thread.sleep(POLLING_INTERVAL);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        // Timed out. Return whatever we have.
        String response = responseBuilder.parsePartialResponse();
        if (getResponseTermination() == null) {
            return response;
        } else {
            throw new TimeoutException(response != null ? "unterminated response: " + response : null);
        }
    }

    @Override
    public void disconnect() {
        if (link != null) {
            link.closePort();
        }
    }
}
