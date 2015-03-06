package org.yamcs.simulator;

import java.net.*;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

class Simulator extends Thread {

	Socket receiveSocket;
	Socket sendSocket;

	CCSDSHandlerFlightData 	flightDataHandler;
	CCSDSHandlerDHS 		dhsHandler;
	CCSDSHandlerPower 		powerDataHandler;
	CCSDSHandlerRCS 		rcsHandler;
	CCSDSHandlerEPSLVPDU 	ESPLvpduHandler;
	CCSDSHandlerAck         AckDataHandler;

	private static Socket connectedSendSocketServer;

	private static Socket connectedReceiveSocketServer;

	private static ServerSocket sendSocketServer;

	private static ServerSocket receiveSocketServer;
	
	private static SocketAddress connectedAddress;


	private boolean engageHoldOneCycle = false;
	private boolean unengageHoldOneCycle = false;
	private int waitToEngage;
	private int waitToUnengage;
    private int DEFAULT_MAX_LENGTH=65542;
	private int maxLength = DEFAULT_MAX_LENGTH;
	private boolean engaged = false;
	private boolean unengaged = true; 
	private boolean ExeTransmitted = true;
	private int battOneCommand;
	private int battTwoCommand;
	private int battThreeCommand;
	private boolean isconnected = false;
	

	public Simulator(Socket ReceiveSocketServer, Socket SendSocketServer) {
		
		this.receiveSocket = ReceiveSocketServer;
		this.sendSocket    = SendSocketServer;

		isconnected = SendSocketServer.isConnected();
		
		flightDataHandler  = new CCSDSHandlerFlightData();
		dhsHandler 		   = new CCSDSHandlerDHS();
		powerDataHandler   = new CCSDSHandlerPower();
		rcsHandler         = new CCSDSHandlerRCS();
		ESPLvpduHandler    = new CCSDSHandlerEPSLVPDU();
		AckDataHandler     = new CCSDSHandlerAck();
 
	}

	public void run() {
		CCSDSPacket receivedPacket = new CCSDSPacket(0, 0, 0);
		CCSDSPacket packet = null;
		DataInputStream dIn = null;
		
		
		if(isconnected)
		{
			try {
				dIn = new DataInputStream(sendSocket.getInputStream());
			} catch (IOException e1) {

				e1.printStackTrace();
			}
		}
		try {

			for (int i = 0;;) {
				CCSDSPacket ExeCompPacket = new CCSDSPacket(3, 2, 8);
				CCSDSPacket flightpacket = new CCSDSPacket(60, 33);
				flightDataHandler.fillPacket(flightpacket);
				flightpacket.send(receiveSocket.getOutputStream());


				if (i < 30) ++i;
				else {
					
					if(waitToEngage == 2 || engaged  ){
						engaged = true;
						//unengaged = false;
						CCSDSPacket Powerpacket = new CCSDSPacket(16 , 1);
						
						powerDataHandler.fillPacket(Powerpacket);
						
						switch(battOneCommand){
							case 1: battOneCommand = 1;
								powerDataHandler.setBattOneOff(Powerpacket);
								AckDataHandler.fillExeCompPacket(ExeCompPacket, 1, 0);
								if (!ExeTransmitted ){
									ExeCompPacket.send(receiveSocket.getOutputStream());
									ExeTransmitted = true;
								}
								break;
								
							case 2: battOneCommand = 2;
								AckDataHandler.fillExeCompPacket(ExeCompPacket, 1, 1);
								if (!ExeTransmitted ){
									ExeCompPacket.send(receiveSocket.getOutputStream());
									ExeTransmitted = true;
								}
								break;
							default :
								break;
						}		
						switch(battTwoCommand){
							
							case 1:battTwoCommand = 1;
								powerDataHandler.setBattTwoOff(Powerpacket);
								AckDataHandler.fillExeCompPacket(ExeCompPacket, 2, 0);
								if (!ExeTransmitted ){
									ExeCompPacket.send(receiveSocket.getOutputStream());
									ExeTransmitted = true;
								}
								break;
								
							case 2:battTwoCommand = 2;
								AckDataHandler.fillExeCompPacket(ExeCompPacket, 2, 1);
								if (!ExeTransmitted ){
									ExeCompPacket.send(receiveSocket.getOutputStream());
									ExeTransmitted = true;
								}
								break;
						}
						switch(battThreeCommand){
							case 1:battThreeCommand = 1;
								powerDataHandler.setBattThreeOff(Powerpacket);
								AckDataHandler.fillExeCompPacket(ExeCompPacket, 3, 0);
								if (!ExeTransmitted ){
									ExeCompPacket.send(receiveSocket.getOutputStream());
									ExeTransmitted = true;
								}
								break;
							case 2:battThreeCommand = 2;
								AckDataHandler.fillExeCompPacket(ExeCompPacket, 3, 1);
								if (!ExeTransmitted ){
									ExeCompPacket.send(receiveSocket.getOutputStream());
									ExeTransmitted = true;
								}
								break;
							default:
								break;
						}
						
						Powerpacket.send(receiveSocket.getOutputStream());

						engageHoldOneCycle = false;
						waitToEngage = 0;
						
						
					}else if (waitToUnengage == 2 || unengaged ){
						CCSDSPacket	Powerpacket = new CCSDSPacket(16 , 1);
						powerDataHandler.fillPacket(Powerpacket);
						Powerpacket.send(receiveSocket.getOutputStream());
						unengaged = true;
						//engaged = false;
						
						unengageHoldOneCycle = false;
						waitToUnengage = 0;
					}	


					packet = new CCSDSPacket(9, 2);
					dhsHandler.fillPacket(packet);
					packet.send(receiveSocket.getOutputStream());

					packet = new CCSDSPacket(36, 3);
					rcsHandler.fillPacket(packet);
					packet.send(receiveSocket.getOutputStream());

					packet = new CCSDSPacket(6, 4);
					ESPLvpduHandler.fillPacket(packet);
					packet.send(receiveSocket.getOutputStream());

					if (engageHoldOneCycle){ // hold the command for 1 cycle after the command Ack received

						waitToEngage = waitToEngage + 1;
						System.out.println("Value : " + waitToEngage);

					}

					if (unengageHoldOneCycle){
						waitToUnengage = waitToUnengage + 1;
					}

					//READ IN COMMAND 
					if(isconnected){
						byte hdr[] = new byte[6];
						dIn.readFully(hdr);
						int remaining=((hdr[4]&0xFF)<<8)+(hdr[5]&0xFF)+1;
						if(remaining>maxLength-6) throw new IOException("Remaining packet length too big: "+remaining+" maximum allowed is "+(maxLength-6));
						byte[] b = new byte[6+remaining];
						System.arraycopy(hdr, 0, b, 0, 6);
						dIn.readFully(b, 6, remaining);
						CCSDSPacket CommandPacket = new CCSDSPacket(ByteBuffer.wrap(b));

						CCSDSPacket ackPacket;
						if (CommandPacket.packetType == 10) {
							System.out.println("BATT COMMAND  : " + receivedPacket.packetid);

							switch(CommandPacket.packetid){

							case 1 : CommandPacket.packetid = 1 ; //switch batt one off
							engageHoldOneCycle = true;
							ExeTransmitted = false;
							battOneCommand = 1;
							ackPacket = new CCSDSPacket(1, 2, 7);
							AckDataHandler.fillAckPacket(ackPacket, 1);
							ackPacket.send(receiveSocket.getOutputStream());
							break;	
							case 2 : CommandPacket.packetid = 2; //switch batt one on
							unengageHoldOneCycle = true;
							//engaged = false;
							ExeTransmitted = false;
							battOneCommand = 2;
							ackPacket = new CCSDSPacket(1, 2, 7);
							AckDataHandler.fillAckPacket(ackPacket, 1);
							ackPacket.send(receiveSocket.getOutputStream());
							break;
							case 3 : CommandPacket.packetid = 3; //switch batt two off
							engageHoldOneCycle = true;
							ExeTransmitted = false;
							battTwoCommand = 1;
							ackPacket = new CCSDSPacket(1, 2, 7);
							AckDataHandler.fillAckPacket(ackPacket, 1);
							ackPacket.send(receiveSocket.getOutputStream());
							break;
							case 4 : CommandPacket.packetid = 4; //switch batt two on
							unengageHoldOneCycle = true;
							//engaged = false;
							ExeTransmitted = false;
							battTwoCommand = 2;
							ackPacket = new CCSDSPacket(1, 2, 7);
							AckDataHandler.fillAckPacket(ackPacket, 1);
							ackPacket.send(receiveSocket.getOutputStream());
							break;
							case 5 : CommandPacket.packetid = 5; //switch batt three off
							engageHoldOneCycle = true;
							ExeTransmitted = false;
							battThreeCommand = 1;
							ackPacket = new CCSDSPacket(1, 2, 7);
							AckDataHandler.fillAckPacket(ackPacket, 1);
							ackPacket.send(receiveSocket.getOutputStream());
							break;	
							case 6 : CommandPacket.packetid = 6; // switch batt three on
							unengageHoldOneCycle = true;
							//engaged = false;
							battThreeCommand = 2;
							ExeTransmitted = false;
							ackPacket = new CCSDSPacket(1, 2, 7);
							AckDataHandler.fillAckPacket(packet, 1);
							ackPacket.send(receiveSocket.getOutputStream());;
							break;
							default : 
							}

						}
					}

					i = 0;	
				}

				Thread.sleep(4000 / 20);
			}

		} catch (IOException e) {
			// System.out.println(e);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("Simulator thread ended");
	}

	public static void main(String[] args) {

	int sendSocketPort = 10025;
	int receiveSocketPort = 10015;

	connectedSendSocketServer = null;
	connectedReceiveSocketServer = null;
	
	

	System.out.println("Waiting on connection " + receiveSocketPort);

	try {
		receiveSocketServer = new ServerSocket(receiveSocketPort);

		connectedReceiveSocketServer = receiveSocketServer.accept();
		System.out.println("connectedReceiveSocketServer: "
				+ connectedReceiveSocketServer.getInetAddress() + ":"
				+ connectedReceiveSocketServer.getPort());
		// receiveSocketServer.setSoTimeout(500);
	} catch (IOException e) {

		e.printStackTrace();
	}

	System.out.println("Waiting on connection " + sendSocketPort);

	System.out.println("Waiting on connection " + sendSocketPort );

	  try {
		  connectedSendSocketServer = new Socket();
		  connectedAddress = new InetSocketAddress("127.0.0.1", sendSocketPort);
		  
		  connectedSendSocketServer.connect(connectedAddress,1000);

	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	Simulator client = new Simulator(connectedReceiveSocketServer,
			connectedSendSocketServer);
	client.start();

}






}
