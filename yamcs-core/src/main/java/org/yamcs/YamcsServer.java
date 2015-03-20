package org.yamcs;

import static org.yamcs.api.Protocol.DATA_TO_HEADER_NAME;
import static org.yamcs.api.Protocol.REPLYTO_HEADER_NAME;
import static org.yamcs.api.Protocol.REQUEST_TYPE_HEADER_NAME;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.core.server.embedded.EmbeddedHornetQ;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.omg.PortableServer.POAManagerPackage.AdapterInactive;
import org.omg.PortableServer.POAPackage.ServantNotActive;
import org.omg.PortableServer.POAPackage.WrongPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.archive.ReplayServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Yamcs.MissionDatabase;
import org.yamcs.protobuf.Yamcs.MissionDatabaseRequest;
import org.yamcs.protobuf.Yamcs.YamcsInstance;
import org.yamcs.protobuf.Yamcs.YamcsInstances;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Service.State;

/**
 * 
 * Main yamcs server, starts a Yarch instance for each defined instance
 * Handles basic requests for retrieving the configured instances, database versions 
 * and retrieve databases in serialized form
 * 
 * @author nm
 *
 */
public class YamcsServer {
    static EmbeddedHornetQ hornetServer;
    static List<String> instances=new ArrayList<String>();

    String instance;
    ReplayServer replay;
    List<Service> serviceList=new ArrayList<Service>();

    Logger log;
    static Logger staticlog=LoggerFactory.getLogger(YamcsServer.class.getName());

    @SuppressWarnings("unchecked")
    YamcsServer(String instance) throws HornetQException, IOException, ConfigurationException, StreamSqlException, ParseException, YamcsApiException {

	this.instance=instance;
	log=LoggerFactory.getLogger(YamcsServer.class.getName()+"["+instance+"]");


	YConfiguration conf=YConfiguration.getConfiguration("yamcs."+instance);
	ManagementService managementService=ManagementService.getInstance();
	List<Object> services=conf.getList("services");
	for(Object servobj:services) {
	    String servclass;
	    Object args = null;
	    if(servobj instanceof String) {
		servclass = (String)servobj;
	    } else if (servobj instanceof Map<?, ?>) {
		Map<String, Object> m = (Map<String, Object>) servobj;
		servclass = YConfiguration.getString(m, "class");
		args = m.get("args");
	    } else {
		throw new ConfigurationException("Services can either be specified by classname, or by {class: classname, args: ....} map. Cannot load a service from "+servobj);
	    }
	    log.info("loading service from "+servclass);
	    YObjectLoader<Service> objLoader = new YObjectLoader<Service>();
	    Service serv;
	    if(args == null) {
		serv = objLoader.loadObject(servclass, instance);
	    } else {
		serv = objLoader.loadObject(servclass, instance, args);
	    }
	    serviceList.add(serv);
	    managementService.registerService(instance, servclass, serv);
	}
	for(Service serv:serviceList) {
	    serv.startAsync();
	    try {
		serv.awaitRunning();
	    } catch (IllegalStateException e) {
		//this happens when it fails, the next check will throw an error in this case
	    }
	    State result = serv.state();
	    if(result==State.FAILED) {
		throw new ConfigurationException("Failed to start service "+serv, serv.failureCause());
	    }
	}
    }


    static YamcsSession yamcsSession;
    static YamcsClient ctrlAddressClient;

    public static EmbeddedHornetQ setupHornet() throws Exception {
	hornetServer = new EmbeddedHornetQ();
	hornetServer.setSecurityManager( new HornetQAuthManager() );
	hornetServer.start();
	//create already the queue here to reduce (but not eliminate :( ) the chance that somebody connects to it before yamcs is started fully
	yamcsSession=YamcsSession.newBuilder().build();
	ctrlAddressClient=yamcsSession.newClientBuilder().setRpcAddress(Protocol.YAMCS_SERVER_CONTROL_ADDRESS).setDataProducer(true).build();
	return hornetServer;
    }
    
    public static void stopHornet() throws Exception {
   	yamcsSession.close();
   	hornetServer.stop();
    }
    
    
    public static boolean hasInstance(String instance) {
	return instances.contains(instance);
    }
    @SuppressWarnings("unchecked")
    public static void setupYamcsServer() throws Exception  {

	YConfiguration c=YConfiguration.getConfiguration("yamcs");
	final List<String>instArray=c.getList("instances");
	for(String inst:instArray) {
	    new YamcsServer(inst);
	    instances.add(inst);
	}

	Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
	    @Override
	    public void uncaughtException(Thread t, Throwable thrown) {
		staticlog.error("Uncaught exception '"+thrown+"' in thread "+t+": "+Arrays.toString(thrown.getStackTrace()));
	    }
	});

	ctrlAddressClient.rpcConsumer.setMessageHandler(new MessageHandler() {
	    @Override
	    public void onMessage(ClientMessage msg) {
		SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
		if(replyto==null) {
		    staticlog.warn("did not receive a replyto header. Ignoring the request");
		    return;
		}
		try {
		    String req=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
		    staticlog.debug("received request "+req);
		    if("getYamcsInstances".equalsIgnoreCase(req)) {
			ctrlAddressClient.sendReply(replyto, "OK", getYamcsInstances());
		    } else  if("getMissionDatabase".equalsIgnoreCase(req)) {
			Privilege priv = HornetQAuthPrivilege.getInstance( msg );
			if( ! priv.hasPrivilege( Privilege.Type.SYSTEM, "MayGetMissionDatabase" ) ) {
			    staticlog.warn( "User '{}' does not have 'MayGetMissionDatabase' privilege." );
			    ctrlAddressClient.sendErrorReply(replyto, "Privilege required but missing: MayGetMissionDatabase");
			    return;
			}
			SimpleString dataAddress=msg.getSimpleStringProperty(DATA_TO_HEADER_NAME);
			if(dataAddress == null) {
			    staticlog.warn("Received a getMissionDatabase without a "+DATA_TO_HEADER_NAME +" header");
			    ctrlAddressClient.sendErrorReply(replyto, "no data address specified");
			    return;
			}
			MissionDatabaseRequest mdr = (MissionDatabaseRequest)Protocol.decode(msg, MissionDatabaseRequest.newBuilder());
			sendMissionDatabase(mdr, replyto, dataAddress);
		    } else {
			staticlog.warn("Received invalid request: "+req);
		    }
		} catch (Exception e) {
		    staticlog.warn("exception received when sending the reply: ", e);
		}
	    }
	});
	System.out.println("yamcsstartup success");
    }

    private static YamcsInstances getYamcsInstances() {
	YamcsInstances.Builder aisb=YamcsInstances.newBuilder();
	for(String inst:instances) {
	    YamcsInstance.Builder aib=YamcsInstance.newBuilder();
	    aib.setName(inst);
	    YConfiguration c;
	    try {
		MissionDatabase.Builder mdb = MissionDatabase.newBuilder(); 
		c = YConfiguration.getConfiguration("yamcs."+inst);
		String configName = c.getString("mdb");
		XtceDb xtcedb=XtceDbFactory.getInstanceByConfig(configName);
		mdb.setConfigName(configName);
		mdb.setName(xtcedb.getRootSpaceSystem().getName());
		Header h =xtcedb.getRootSpaceSystem().getHeader();
		if((h!=null) && (h.getVersion()!=null)) {
		    mdb.setVersion(h.getVersion());
		}
		aib.setMissionDatabase(mdb.build());
	    } catch (ConfigurationException e) {
		staticlog.warn("Got error when finding the mission database for instance "+inst, e);
	    }
	    aisb.addInstance(aib.build());
	}
	return aisb.build();
    }

    private static void sendMissionDatabase(MissionDatabaseRequest mdr, SimpleString replyTo, SimpleString dataAddress) throws HornetQException {
	final XtceDb xtcedb;
	try {
	    if(mdr.hasInstance()) {
		xtcedb=XtceDbFactory.getInstance(mdr.getInstance());
	    } else if(mdr.hasDbConfigName()){
		xtcedb=XtceDbFactory.getInstanceByConfig(mdr.getDbConfigName());
	    } else {
		staticlog.warn("getMissionDatabase request received with none of the instance or dbConfigName specified");
		ctrlAddressClient.sendErrorReply(replyTo, "Please specify either instance or dbConfigName");
		return;
	    }

	    ClientMessage msg=yamcsSession.session.createMessage(false);
	    ObjectOutputStream oos=new ObjectOutputStream(new ChannelBufferOutputStream(msg.getBodyBuffer().channelBuffer()));
	    oos.writeObject(xtcedb);
	    oos.close();
	    ctrlAddressClient.sendReply(replyTo, "OK", null);
	    ctrlAddressClient.dataProducer.send(dataAddress, msg);
	} catch (ConfigurationException e) {
	    YamcsException ye=new YamcsException(e.toString());
	    ctrlAddressClient.sendErrorReply(replyTo, ye);
	} catch (IOException e) { //this should not happen since all the ObjectOutputStream happens in memory
	    throw new RuntimeException(e);
	}
    }

    public static void configureNonBlocking(SimpleString dataAddress) {
	//TODO
	Object o=hornetServer.getHornetQServer().getManagementService().getResource(dataAddress.toString());
    }

    /**
     * @param args
     * @throws ConfigurationException 
     * @throws IOException 
     * @throws ChannelException 
     * @throws InvalidName 
     * @throws AdapterInactive 
     * @throws WrongPolicy 
     * @throws ServantNotActive 
     * @throws java.text.ParseException 
     */
    public static void main(String[] args) {
	if(args.length>0) printOptionsAndExit();

	try {
	    YConfiguration.setup();
	    setupHornet();
	    org.yamcs.yarch.management.ManagementService.setup(true);
	    ManagementService.setup(true,true);
	    setupYamcsServer();

	} catch (ConfigurationException e) {
	    staticlog.error("Could not start Yamcs Server: ", e);
	    System.err.println(e.toString());
	    System.exit(-1);
	} catch (Throwable e) {
	    staticlog.error("Could not start Yamcs Server: ", e);
	    e.printStackTrace();
	    System.exit(-1);
	}

    }


    private static void printOptionsAndExit() {
	System.err.println("Usage: yamcs-server.sh");
	System.err.println("\t All options are taken from yamcs.yaml");
	System.exit(-1);
    }

   
}