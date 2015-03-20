package org.yamcs.yarch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.TokenMgrError;


public class Yarch implements Runnable {
    Socket socket;
    Logger log=LoggerFactory.getLogger(this.getClass().getName());

    Yarch(Socket socket) throws IOException {
	log.info("new client connected from :"+socket.getRemoteSocketAddress());
	this.socket=socket;
    }

    public void run() {
	try {
	    BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
	    BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
	    String line;
	    ExecutionContext context=new ExecutionContext("db1");
	    while((line=reader.readLine())!=null) {
		log.debug("Received '"+line+"'");
		if(line.length()==0)continue;
		StreamSqlParser parser=new StreamSqlParser(new java.io.StringReader(line));
		try {
		    StreamSqlResult res=parser.StreamSqlStatement().execute(context);
		    String s=res.toString();
		    if (s==null) writer.append("OK");
		    else writer.append("OK "+s);
		} catch (StreamSqlException e) {
		    log.info("Got error from parser: "+e.getMessage());
		    writer.append(e.getMessage());
		} catch(TokenMgrError e) {
		    log.info("Got error from parser: "+e.getMessage());
		    writer.append("ERROR ");
		    writer.append(e.getMessage());
		} catch (Exception e) {
		    log.info("Got error from parser: "+e.getMessage());
		    writer.append("ERROR ");
		    writer.append(e.getMessage());
		}
		writer.append("\n");writer.flush();
	    }
	    log.info("Connection from "+socket.getRemoteSocketAddress()+" closed");
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}

    }


    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
	ServerSocket serverSocket=new ServerSocket(8062);
	while (true) {
	    Socket socket=serverSocket.accept();
	    Yarch mrdp=new Yarch(socket);
	    (new Thread(mrdp)).start();
	}
    }
}
