package com.spaceapplications.yamcs.scpi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.fazecast.jSerialComm.SerialPort;
import com.spaceapplications.yamcs.scpi.commander.DeviceConnect.Device;

public class SerialDevice implements Device {
  private String id;
  private String devicePath;
  private static SerialPort sp;

  public SerialDevice(String id, String devicePath) {
    this.id = id;
    this.devicePath = devicePath;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public synchronized void open() {
    if (sp == null) {
      sp = SerialPort.getCommPort(devicePath);
      sp.setBaudRate(9600);
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