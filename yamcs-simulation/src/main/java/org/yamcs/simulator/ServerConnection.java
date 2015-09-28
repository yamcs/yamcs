package org.yamcs.simulator;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;


public class ServerConnection {

    public enum ConnectionStatus{
        NOT_CONNECTED,
        CONNECTING,
        CONNECTED,
        CONNECTION_LOST
    }

	private boolean connected = false;
	private boolean signalStatus = false;

	private Socket tmSocket;
	private Socket tcSocket;
	private Socket losSocket;

	private ServerSocket tmServerSocket;
	private ServerSocket tcServerSocket;
	private ServerSocket losServerSocket;

	private int tmPort;
	private int tcPort;
	private int losPort;
		
	private int id;
	
	Queue<CCSDSPacket> tmQueue = new ArrayBlockingQueue<>(1000);//no more than 100 pending commands
	Queue<CCSDSPacket> tmDumpQueue = new ArrayBlockingQueue<>(1000);

	public ServerConnection(int id, int tmPort, int tcPort, int losPort) {
        this.id = id;
		this.tmPort = tmPort;
		this.tcPort = tcPort;
		this.losPort = losPort;
	}
	
	public Socket getTcSocket() {
		return tcSocket;
	}

	public void setTcSocket(Socket tcSocket) {
		this.tcSocket = tcSocket;
	}

	public Socket getTmSocket() {
		return tmSocket;
	}

	public void setTmSocket(Socket tmSocket) {
		this.tmSocket = tmSocket;
	}
	
	public int getTcPort() {
		return tcPort;
	}

	public void setTcPort(int tcPort) {
		this.tcPort = tcPort;
	}

	public int getTmPort() {
		return tmPort;
	}

	public void setTmPort(int tmPort) {
		this.tmPort = tmPort;
	}

	public ServerSocket getTmServerSocket() {
		return tmServerSocket;
	}

	public void setTmServerSocket(ServerSocket tmServerSocket) {
		this.tmServerSocket = tmServerSocket;
	}

	public ServerSocket getTcServerSocket() {
		return tcServerSocket;
	}

	public void setTcServerSocket(ServerSocket tcServerSocket) {
		this.tcServerSocket = tcServerSocket;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public  CCSDSPacket getTmPacket() {
		return tmQueue.remove();
	}

	public void queueTmPacket(CCSDSPacket packet) {
		this.tmQueue.add(packet);
	}
	
	public boolean isTmQueueEmpty() {
		return tmQueue.isEmpty();
	}
	
	// tm dump related
	public CCSDSPacket getTmDumpPacket() {
		return tmDumpQueue.remove();
	}

	public void setTmDumpPacket(CCSDSPacket packet) {
		this.tmDumpQueue.add(packet);
	}
	
	public boolean isTmDumpQueueEmpty() {
		return tmDumpQueue.isEmpty();
	}
	
	public boolean isSignalStatus() {
		return signalStatus;
	}

	public void setSignalStatus(boolean signalStatus) {
		this.signalStatus = signalStatus;
	}

	public ServerSocket getLosServerSocket() {
		return losServerSocket;
	}

	public void setLosServerSocket(ServerSocket losServerSocket) {
		this.losServerSocket = losServerSocket;
	}
	
	public Socket getLosSocket() {
		return losSocket;
	}

	public void setLosSocket(Socket losSocket) {
		this.losSocket = losSocket;
	}

	public int getLosPort() {
		return losPort;
	}

	public void setLosPort(int losPort) {
		this.losPort = losPort;
	}
}
