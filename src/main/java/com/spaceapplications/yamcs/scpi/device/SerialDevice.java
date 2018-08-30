package com.spaceapplications.yamcs.scpi.device;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import com.fazecast.jSerialComm.SerialPort;

public class SerialDevice implements Device {
    private static final int DEFAULT_BAUD_RATE = 9600;
    private static SerialPort sp;

    private String id;
    private String devicePath;
    private Optional<Integer> baudrate;

    public SerialDevice(String id, String devicePath, Optional<Integer> baudrate) {
        this.id = id;
        this.devicePath = devicePath;
        this.baudrate = baudrate;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public synchronized void open() {
        if (sp == null) {
            sp = SerialPort.getCommPort(devicePath);
            sp.setBaudRate(baudrate.orElse(DEFAULT_BAUD_RATE));
            sp.openPort();
            sp.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
        }
    }

    @Override
    public synchronized String exec(String cmd) {
        byte[] bytes = cmd.getBytes();
        sp.writeBytes(bytes, bytes.length);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] singleByte = { 0 };
        while (sp.readBytes(singleByte, 1) > 0) {
            try {
                buf.write(singleByte);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return buf.toString();
    }

    @Override
    public synchronized void close() {
    }
}
