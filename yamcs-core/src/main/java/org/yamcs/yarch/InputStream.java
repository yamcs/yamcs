package org.yamcs.yarch;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class InputStream extends AbstractStream implements Runnable {
	ServerSocket serverSocket;
	
	
	
	public InputStream(YarchDatabase dict, String name, TupleDefinition def) throws YarchException {
	    
		super(dict, name, def);
		if(true) throw new YarchException("TO reimplement");
		try {
		
		    serverSocket=new ServerSocket(0);
		} catch (IOException e) {
			throw new YarchException(e);
		}
	}
	
	
	@Override
	public void start() {
	  state=RUNNING;
		new Thread(this).start();
	}
	
	public int getPort() {
		return serverSocket.getLocalPort();
	}

  boolean first=true;

	public void run() {
   
	  log.info("Started new input Stream with definition "+outputDefinition+" listening on port "+getPort());
		try {
			while(state!=QUITTING) {
			  Socket socket=serverSocket.accept();
			  if(first)first=false;
        else throw new RuntimeException("arghhh");
			  log.debug("starting a new thread for {}", name);
			  (new Thread(new SocketReader(socket))).start();
			}
		} catch (IOException e) {
			if(state==QUITTING) return;
			log.warn("Exception caught when reading from socket: ", e);
		}
	}
	
	@Override
	public void doClose() {
		log.info("Closing input stream "+name);
		try {
			serverSocket.close();
		} catch (IOException e) {
			log.warn("Got exception when closing the sockets:", e);
		}
	}
	
	@Override
	public String toString() {
		return "INPUT STREAM "+name+"("+outputDefinition.toString()+")";
	}
	
	
	class SocketReader implements Runnable{
		Socket socket;
		public SocketReader(Socket s) {
			this.socket=s;
		}
		
		//we have one thread running this for each input socket - using multiplexed java.nio is more difficult because we have to read entire tuples
		public void run() {
	/*		
		  try {
		    DataInputStream dis=new DataInputStream(new BufferedInputStream(socket.getInputStream()));
		    while(state!=QUITTING) {
					Tuple t=outputDefinition.read(dis);				
					if(t==null) {
						log.info("Socket at port "+socket.getPort()+" closed");
						break;
					} else {
						log.trace("Received tuple t: ",t);
						emitTuple(t);
					}
				}
				socket.close();
			} catch (IOException e) {
				if(state==QUITTING) return;
				log.warn("Exception caught when reading from socket: "+e);
			}*/
		}
		
	}
}
