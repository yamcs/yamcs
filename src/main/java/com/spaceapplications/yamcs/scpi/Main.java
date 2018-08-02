package com.spaceapplications.yamcs.scpi;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

public class Main {
  public static void main(String[] args) {
    new Main();
  }

  public Main() {
    printPorts();

    SerialPort sp = SerialPort.getCommPort("/dev/tty.usbmodem1411");
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

    try {
      Thread.sleep(5000);
    } catch (Exception e) {
      e.printStackTrace();
    }
    sp.closePort();
  }

  private void printPorts() {
    for (SerialPort sp : SerialPort.getCommPorts()) {
      System.out.println(sp.getSystemPortName());
    }
  }
}