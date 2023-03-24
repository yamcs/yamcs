package org.yamcs.tse;

import java.io.IOException;

import org.yamcs.YConfiguration;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Connect and command a device over a serial port.
 * 
 * Not thread safe.
 */
public class SerialPortDriver extends InstrumentDriver {
    private SerialPort link;

    private String devicePath;
    private int baudrate = 9600;
    private int dataBits = 8;
    private String parity;

    @Override
    public void init(String name, YConfiguration config) {
        super.init(name, config);
        this.devicePath = config.getString("path");

        if (config.containsKey("baudrate")) {
            baudrate = config.getInt("baudrate");
        }
        if (config.containsKey("dataBits")) {
            dataBits = config.getInt("dataBits");
        }
        if (config.containsKey("parity")) {
            parity = config.getString("parity");
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
    public void write(byte[] bytes) {
        link.writeBytes(bytes, bytes.length);
    }

    @Override
    public void readAvailable(ResponseBuffer responseBuffer, int timeout) throws IOException {
        try {
            int n = link.bytesAvailable();
            if (n == 0) {
                Thread.sleep(timeout);
                n = link.bytesAvailable();
            }
            if (n > 0) {
                byte[] buf = new byte[n];
                link.readBytes(buf, n);
                responseBuffer.append(buf, 0, n);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }

    @Override
    public void disconnect() {
        if (link != null) {
            link.closePort();
        }
    }

    @Override
    public String getDefaultRequestTermination() {
        return null;
    }

    @Override
    public boolean isFragmented() {
        return true;
    }
}
