package com.spaceapplications.yamcs.scpi;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.spaceapplications.yamcs.scpi.commander.DeviceConnect.Device;

public class SerialDevice implements Device {
  private String id;
  private String devicePath;
  private SerialPort sp;


  @Override
  public void open() {
    SerialPort sp = SerialPort.getCommPort(devicePath);
    sp.setBaudRate(9600);
    sp.openPort();

    sp.addDataListener(new SerialPortDataListener() {
      @Override
      public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
      }

      @Override
      public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
          return;

        int bytes = sp.bytesAvailable();
        System.out.print(bytes + "available: ");
        byte[] newData = new byte[sp.bytesAvailable()];
        sp.readBytes(newData, newData.length);
        System.out.println(new String(newData));
      }
    });

    byte[] msg = "VOUT1?".getBytes();
    sp.writeBytes(msg, msg.length);

  }

  public SerialDevice(String id, String devicePath) {
    this.id = id;
    this.devicePath = devicePath;
  }

  private void serial() {

    try {
      Thread.sleep(5000);
    } catch (Exception e) {
      e.printStackTrace();
    }
    sp.closePort();
  }

  @Override
  public String id() {
    return id;
  }


  @Override
  public void close() {
    sp.removeDataListener();
  }

  @Override
  public String exec(String cmd) {
    return "exec: " + cmd + " on " + id;
  }
}