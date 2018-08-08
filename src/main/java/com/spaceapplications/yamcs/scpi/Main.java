package com.spaceapplications.yamcs.scpi;

import static pl.touk.throwing.ThrowingSupplier.unchecked;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class Main {
  public static Integer DEFAULT_PORT = 1337;
  public static Integer MAX_INOMING_CONNECTIONS = 5;

  public static void main(String[] args) {
    new Main(args);
  }

  public Main(String[] args) {
    EventLoopGroup bossEventLoop = new NioEventLoopGroup();
    EventLoopGroup workerEventLoop = new NioEventLoopGroup();
    
    try {
      ServerBootstrap b = bootstrap(bossEventLoop, workerEventLoop);
      ChannelFuture f = unchecked(b.bind(DEFAULT_PORT)::sync).get();
      unchecked(f.channel().closeFuture()::sync).get();
    } finally {
      bossEventLoop.shutdownGracefully();
      workerEventLoop.shutdownGracefully();
    }
  }

  private ServerBootstrap bootstrap(EventLoopGroup bossEventLoop, EventLoopGroup workerEventLoop) {
    return new ServerBootstrap()
      .group(bossEventLoop, workerEventLoop)
      .channel(NioServerSocketChannel.class)
      .option(ChannelOption.SO_BACKLOG, MAX_INOMING_CONNECTIONS)
      .handler(new LoggingHandler(LogLevel.INFO))
      .childHandler(new ServerInitializer());
  }

  private void serial() {
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