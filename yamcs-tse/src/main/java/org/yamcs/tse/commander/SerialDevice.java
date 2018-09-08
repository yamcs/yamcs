package org.yamcs.tse.commander;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Connect and command a device over a serial port.
 */
public class SerialDevice extends Device {

    private static final int POLLING_INTERVAL = 20;

    private static SerialPort link;

    private String devicePath;
    private int baudrate = 9600;
    private int dataBits = 8;
    private String parity;

    public SerialDevice(String id, String devicePath) {
        super(id);
        this.devicePath = devicePath;
    }

    public int getBaudrate() {
        return baudrate;
    }

    public void setBaudrate(int baudrate) {
        this.baudrate = baudrate;
    }

    public int getDataBits() {
        return dataBits;
    }

    public void setDataBits(int dataBits) {
        this.dataBits = dataBits;
    }

    public String getParity() {
        return parity;
    }

    public void setParity(String parity) {
        this.parity = parity;
    }

    public String getPath() {
        return devicePath;
    }

    @Override
    public synchronized void connect() {
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
    public synchronized void write(String cmd) {
        byte[] bytes = cmd.getBytes();
        link.writeBytes(bytes, bytes.length);
    }

    @Override
    public synchronized String read() throws IOException, InterruptedException {
        long time = System.currentTimeMillis();
        long timeoutTime = time + responseTimeout;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        while (System.currentTimeMillis() < timeoutTime) {
            int n = link.bytesAvailable();
            if (n > 0) {
                byte[] buf = new byte[n];
                link.readBytes(buf, n);
                bout.write(buf);
            }
            Thread.sleep(POLLING_INTERVAL);
        }

        byte[] barr = bout.toByteArray();
        return (barr.length > 0) ? new String(barr, encoding) : null;
    }

    @Override
    public synchronized void disconnect() {
        if (link != null) {
            link.closePort();
        }
    }
}
