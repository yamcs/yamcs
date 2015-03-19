package org.yamcs.simulator;

import java.net.*;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;


class Simulator extends Thread {

	Socket tmSocket;
	Socket tcSocket;

	CCSDSHandlerFlightData 	flightDataHandler;
	CCSDSHandlerDHS 		dhsHandler;
	CCSDSHandlerPower 		powerDataHandler;
	CCSDSHandlerRCS 		rcsHandler;
	CCSDSHandlerEPSLVPDU 	ESPLvpduHandler;
	CCSDSHandlerAck         AckDataHandler;


	private static Socket connectedTmSocket;

	private static ServerSocket tcSocketServer;

	private static ServerSocket tmSocketServer;


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
	Queue<CCSDSPacket> pendingCommands = new ArrayBlockingQueue<CCSDSPacket>(100);//no more than 100 pending commands



	public Simulator(Socket connectedTmSocket, Socket connectedTcSocket) throws IOException {


		this.tmSocket = connectedTmSocket;
		this.tcSocket    = connectedTcSocket;

		flightDataHandler  = new CCSDSHandlerFlightData();
		dhsHandler 		   = new CCSDSHandlerDHS();
		powerDataHandler   = new CCSDSHandlerPower();
		rcsHandler         = new CCSDSHandlerRCS();
		ESPLvpduHandler    = new CCSDSHandlerEPSLVPDU();
		AckDataHandler     = new CCSDSHandlerAck();

	}

	public void run() {

		//start the TC reception thread;
		(new Thread(new Runnable() {
			@Override
			public void run() {
				readCommands();
			}
		})).start();




		CCSDSPacket packet = null;

		try {

			for (int i = 0;;) {
				CCSDSPacket ExeCompPacket = new CCSDSPacket(3, 2, 8);
				CCSDSPacket flightpacket = new CCSDSPacket(60, 33);
				flightDataHandler.fillPacket(flightpacket);
				flightpacket.send(tmSocket.getOutputStream());


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
							ExeCompPacket.send(tmSocket.getOutputStream());
							ExeTransmitted = true;
						}
						break;

						case 2: battOneCommand = 2;
						AckDataHandler.fillExeCompPacket(ExeCompPacket, 1, 1);
						if (!ExeTransmitted ){
							ExeCompPacket.send(tmSocket.getOutputStream());
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
							ExeCompPacket.send(tmSocket.getOutputStream());
							ExeTransmitted = true;
						}
						break;

						case 2:battTwoCommand = 2;
						AckDataHandler.fillExeCompPacket(ExeCompPacket, 2, 1);
						if (!ExeTransmitted ){
							ExeCompPacket.send(tmSocket.getOutputStream());
							ExeTransmitted = true;
						}
						break;
						}
						switch(battThreeCommand){
						case 1:battThreeCommand = 1;
						powerDataHandler.setBattThreeOff(Powerpacket);
						AckDataHandler.fillExeCompPacket(ExeCompPacket, 3, 0);
						if (!ExeTransmitted ){
							ExeCompPacket.send(tmSocket.getOutputStream());
							ExeTransmitted = true;
						}
						break;
						case 2:battThreeCommand = 2;
						AckDataHandler.fillExeCompPacket(ExeCompPacket, 3, 1);
						if (!ExeTransmitted ){
							ExeCompPacket.send(tmSocket.getOutputStream());
							ExeTransmitted = true;
						}
						break;
						default:
							break;
						}

						Powerpacket.send(tmSocket.getOutputStream());

						engageHoldOneCycle = false;
						waitToEngage = 0;


					} else if (waitToUnengage == 2 || unengaged ){
						CCSDSPacket	Powerpacket = new CCSDSPacket(16 , 1);
						powerDataHandler.fillPacket(Powerpacket);
						Powerpacket.send(tmSocket.getOutputStream());
						unengaged = true;
						//engaged = false;

						unengageHoldOneCycle = false;
						waitToUnengage = 0;
					}	


					packet = new CCSDSPacket(9, 2);
					dhsHandler.fillPacket(packet);
					packet.send(tmSocket.getOutputStream());

					packet = new CCSDSPacket(36, 3);
					rcsHandler.fillPacket(packet);
					packet.send(tmSocket.getOutputStream());

					packet = new CCSDSPacket(6, 4);
					ESPLvpduHandler.fillPacket(packet);
					packet.send(tmSocket.getOutputStream());

					if (engageHoldOneCycle){ // hold the command for 1 cycle after the command Ack received

						waitToEngage = waitToEngage + 1;
						System.out.println("Value : " + waitToEngage);

					}

					if (unengageHoldOneCycle){
						waitToUnengage = waitToUnengage + 1;
					}

					i = 0;	
				}

				executePendingCommands();
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

	/**
	 * runs in the main TM thread, executes commands from the queue (if any)
	 * @throws IOException 
	 */
	private void executePendingCommands() throws IOException {
		while(pendingCommands.size()>0) {
			CCSDSPacket commandPacket = pendingCommands.poll();

			CCSDSPacket ackPacket;
			if (commandPacket.packetType == 10) {
				System.out.println("BATT COMMAND  : " + commandPacket.packetid+" batNum: "+commandPacket.getUserDataBuffer().get(0));

				switch(commandPacket.packetid){

				case 1 : commandPacket.packetid = 1 ; //switch on 
					int batNum = commandPacket.getUserDataBuffer().get(0);
					switch(batNum) {
					case 1: batNum = 1; //switch bat1 on
						unengageHoldOneCycle = true;
						//engaged = false;
						ExeTransmitted = false;
						battOneCommand = 2;
						ackPacket = new CCSDSPacket(1, 2, 7);
						AckDataHandler.fillAckPacket(ackPacket, 1);
						ackPacket.send(tmSocket.getOutputStream());
					case 2: batNum = 2 ; //swtich bat2 on
						unengageHoldOneCycle = true;
						//engaged = false;
						ExeTransmitted = false;
						battTwoCommand = 2;
						ackPacket = new CCSDSPacket(1, 2, 7);
						AckDataHandler.fillAckPacket(ackPacket, 1);
						ackPacket.send(tmSocket.getOutputStream());
					case 3: batNum = 3;  //switch bat3 on
						unengageHoldOneCycle = true;
						//engaged = false;
						battThreeCommand = 2;
						ExeTransmitted = false;
						ackPacket = new CCSDSPacket(1, 2, 7);
						AckDataHandler.fillAckPacket(ackPacket, 1);
						ackPacket.send(tmSocket.getOutputStream());;
					}
					break;
				case 2: commandPacket.packetid = 2 ; //switch off
					batNum = commandPacket.getUserDataBuffer().get(0);
					switch(batNum) {
				
					case 1: batNum = 1; //switch bat1 off
						engageHoldOneCycle = true;
						ExeTransmitted = false;
						battOneCommand = 1;
						ackPacket = new CCSDSPacket(1, 2, 7);
						AckDataHandler.fillAckPacket(ackPacket, 1);
						ackPacket.send(tmSocket.getOutputStream());
					break;
					case 2: batNum = 2; //switch bat2 off
						engageHoldOneCycle = true;
						ExeTransmitted = false;
						battTwoCommand = 1;
						ackPacket = new CCSDSPacket(1, 2, 7);
						AckDataHandler.fillAckPacket(ackPacket, 1);
						ackPacket.send(tmSocket.getOutputStream());
					break;
					case 3: batNum = 3; //switch bat3 off
						engageHoldOneCycle = true;
						ExeTransmitted = false;
						battThreeCommand = 1;
						ackPacket = new CCSDSPacket(1, 2, 7);
						AckDataHandler.fillAckPacket(ackPacket, 1);
						ackPacket.send(tmSocket.getOutputStream());
					}
				}
			}
		}
	}

	/**
	 * this runs in a separate thread but pushes commands to the main TM thread
	 * @throws IOException 
	 * 
	 */
	private void readCommands() {
		try {

			DataInputStream dIn = new DataInputStream(tcSocket.getInputStream());				
			while(true) {
				//READ IN COMMAND 
				byte hdr[] = new byte[6];
				dIn.readFully(hdr);
				int remaining=((hdr[4]&0xFF)<<8)+(hdr[5]&0xFF)+1;
				if(remaining>maxLength-6) throw new IOException("Remaining packet length too big: "+remaining+" maximum allowed is "+(maxLength-6));
				byte[] b = new byte[6+remaining];
				System.arraycopy(hdr, 0, b, 0, 6);
				dIn.readFully(b, 6, remaining);
				CCSDSPacket commandPacket = new CCSDSPacket(ByteBuffer.wrap(b));
				System.out.println("received commandPacket "+commandPacket.toString());
				pendingCommands.add(commandPacket);
			}
		} catch(Exception e) {
			System.err.println("Error reading command");
			e.printStackTrace();
			return;
		}

	}

	public static void main(String[] args) {

		int tcSocketPort = 10025;
		int receiveSocketPort = 10015;

		connectedTmSocket = null;


		System.out.println("Waiting on TM connection " + receiveSocketPort);

		try {
			tmSocketServer = new ServerSocket(receiveSocketPort);

			connectedTmSocket = tmSocketServer.accept();
			System.out.println("connectedReceiveSocketServer: "
					+ connectedTmSocket.getInetAddress() + ":"
					+ connectedTmSocket.getPort());
			// receiveSocketServer.setSoTimeout(500);
		} catch (IOException e) {

			e.printStackTrace();
		}

		System.out.println("Waiting on TC connections on " + tcSocketPort);

		try {
			tcSocketServer = new ServerSocket(tcSocketPort);
			Socket connectedTcSocket = tcSocketServer.accept();


			System.out.println("connectedTCSocketServer: "
					+ connectedTmSocket.getInetAddress() + ":"
					+ connectedTmSocket.getPort());

			Simulator client = new Simulator(connectedTmSocket,	connectedTcSocket);
			client.start();

		} catch (Exception e) {
			e.printStackTrace();
		}

	}


}
