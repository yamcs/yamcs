package org.yamcs;

import static org.yamcs.api.Protocol.DATA_TO_HEADER_NAME;
import static org.yamcs.api.Protocol.REPLYTO_HEADER_NAME;
import static org.yamcs.api.Protocol.REQUEST_TYPE_HEADER_NAME;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.core.server.embedded.EmbeddedHornetQ;
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
import org.yamcs.protobuf.YamcsManagement.MissionDatabase;
import org.yamcs.protobuf.YamcsManagement.MissionDatabaseRequest;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstances;
import org.yamcs.security.HornetQAuthManager;
import org.yamcs.security.HqClientMessageToken;
import org.yamcs.security.Privilege;
import org.yamcs.time.RealtimeTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.HornetQBufferOutputStream;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.web.HttpServer;
import org.yamcs.web.StaticFileHandler;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

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
    static Map<String, YamcsServer> instances=new LinkedHashMap<String, YamcsServer>();
    final static private String SERVER_ID_KEY="serverId";

    String instance;
    ReplayServer replay;
    List<Service> serviceList=new ArrayList<Service>();

    Logger log;
    static Logger staticlog=LoggerFactory.getLogger(YamcsServer.class);

    /**in the shutdown, allow servies this number of seconds for stopping*/
    public static int SERVICE_STOP_GRACE_TIME = 10;
    TimeService timeService;
    
    static TimeService realtimeTimeService = new RealtimeTimeService();
    
    //used for unit tests
    static TimeService mockupTimeService;
    
    static private String serverId;
    
    @SuppressWarnings("unchecked")
    YamcsServer(String instance) throws HornetQException, IOException, StreamSqlException, ParseException, YamcsApiException {
        this.instance=instance;
        
        //TODO - fix bootstrap issue 
        instances.put(instance, this);
        
        log=getLogger(YamcsServer.class, instance);
        
        YConfiguration conf=YConfiguration.getConfiguration("yamcs."+instance);
        loadTimeService();
                
        ManagementService managementService=ManagementService.getInstance();
        StreamInitializer.createStreams(instance);
        YProcessor.addProcessorListener(managementService);
        
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
            log.info("Loading service "+servclass);
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
    
    public static HttpServer setupHttpServer() {
        StaticFileHandler.init();
        return HttpServer.getInstance();
    }

    static YamcsSession yamcsSession;
    static YamcsClient ctrlAddressClient;

    public static EmbeddedHornetQ setupHornet() throws Exception {
        //divert hornetq logging
        System.setProperty("org.jboss.logging.provider", "slf4j");

        // load optional configuration file name for hornetq,
        // otherwise default will be hornetq-configuration.xml
        String hornetqConfigFile = null;
        try{
            YConfiguration c=YConfiguration.getConfiguration("yamcs");
            hornetqConfigFile = c.getString("hornetqConfigFile");
        }
        catch (Exception e){}

        hornetServer = new EmbeddedHornetQ();
        hornetServer.setSecurityManager( new HornetQAuthManager() );
        if(hornetqConfigFile != null)
            hornetServer.setConfigResourcePath(hornetqConfigFile);
        hornetServer.start();
        //create already the queue here to reduce (but not eliminate :( ) the chance that somebody connects to it before yamcs is started fully
        yamcsSession=YamcsSession.newBuilder().build();
        ctrlAddressClient=yamcsSession.newClientBuilder().setRpcAddress(Protocol.YAMCS_SERVER_CONTROL_ADDRESS).setDataProducer(true).build();
        return hornetServer;
    }

    public static void stopHornet() throws Exception {
        yamcsSession.close();
        Protocol.closeKiller();
        hornetServer.stop();
    }

    public static void shutDown() throws Exception {
        for(YamcsServer ys: instances.values()) {
            ys.stop();
        }
    }
    
    /**
     * Return a logger decorated with the applicable yamcs instance 
     * <p>Convenience method
     */
    public static Logger getLogger(Class<?> clazz, String instance) {
        return LoggerFactory.getLogger(clazz.getName() + "["+instance+"]");
    }
    
    /**
     * Return a logger decorated with the applicable yamcs instance and processor 
     * <p>Convenience method
     */
    public static Logger getLogger(Class<?> clazz, YProcessor processor) {
        return LoggerFactory.getLogger(clazz.getName() + "["+processor.getInstance()+"/" +processor.getName()+ "]");
    }

    public void stop() {
        for(int i = serviceList.size()-1; i>=0; i--) {
            Service s = serviceList.get(i);
            s.stopAsync();
            try {
                s.awaitTerminated(SERVICE_STOP_GRACE_TIME, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.error("Service "+s+" did not stop in "+SERVICE_STOP_GRACE_TIME + " seconds");
            }
        }
    }

    public static boolean hasInstance(String instance) {
        return instances.containsKey(instance);
    }
    
    public static String getServerId() {
        return serverId;
    }

    public static void setupYamcsServer() throws Exception  {
        YConfiguration c=YConfiguration.getConfiguration("yamcs");
        final List<String>instArray=c.getList("instances");
        
        if (instArray.isEmpty()) {
            staticlog.warn("No instances");
        } else if (instArray.size() == 1) {
            staticlog.info("1 instance: " + instArray.get(0));
        } else {
            staticlog.info(instArray.size() + " instances: " + String.join(", ", instArray));
        }
        
        for(String inst:instArray) {
            instances.put(inst, new YamcsServer(inst));
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

                        Privilege priv = Privilege.getInstance();
                        HqClientMessageToken authToken = new HqClientMessageToken(msg, null);
                        if( ! priv.hasPrivilege(authToken, Privilege.Type.SYSTEM, "MayGetMissionDatabase" ) ) {
                            staticlog.warn("User '{}' does not have 'MayGetMissionDatabase' privilege.");
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
        if(System.getenv("YAMCS_DAEMON")==null) {
            staticlog.info("Server running... press ctrl-c to stop");
        }
        else
        {
            staticlog.info("yamcsstartup success");
        }
    }

    public static YamcsInstances getYamcsInstances() {
        YamcsInstances.Builder aisb=YamcsInstances.newBuilder();
        for(String name : instances.keySet()) {
            aisb.addInstance(getYamcsInstance(name));
        }
        return aisb.build();
    }
    
    public static YamcsInstance getYamcsInstance(String name) {
        if (!hasInstance(name)) return null;
        YamcsInstance.Builder aib=YamcsInstance.newBuilder();
        aib.setName(name);
        try {
            MissionDatabase.Builder mdb = MissionDatabase.newBuilder();
            YConfiguration c = YConfiguration.getConfiguration("yamcs."+name);
            String configName = c.getString("mdb");
            XtceDb xtcedb=XtceDbFactory.getInstanceByConfig(name, configName);
            mdb.setConfigName(configName);
            mdb.setName(xtcedb.getRootSpaceSystem().getName());
            Header h =xtcedb.getRootSpaceSystem().getHeader();
            if((h!=null) && (h.getVersion()!=null)) {
                mdb.setVersion(h.getVersion());
            }
            aib.setMissionDatabase(mdb.build());
        } catch (ConfigurationException e) {
            staticlog.warn("Got error when finding the mission database for instance "+name, e);
        }
        return aib.build();
    }

    private static void sendMissionDatabase(MissionDatabaseRequest mdr, SimpleString replyTo, SimpleString dataAddress) throws HornetQException {
        final XtceDb xtcedb;
        try {
            if(mdr.hasInstance()) {
                xtcedb=XtceDbFactory.getInstance(mdr.getInstance());
            } else if(mdr.hasDbConfigName()){
                xtcedb=XtceDbFactory.createInstance(mdr.getDbConfigName());
            } else {
                staticlog.warn("getMissionDatabase request received with none of the instance or dbConfigName specified");
                ctrlAddressClient.sendErrorReply(replyTo, "Please specify either instance or dbConfigName");
                return;
            }
        
            ClientMessage msg=yamcsSession.session.createMessage(false);
            ObjectOutputStream oos=new ObjectOutputStream(new HornetQBufferOutputStream(msg.getBodyBuffer()));
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
    
    private static String deriveServerId() {
        try {
            YConfiguration yconf = YConfiguration.getConfiguration("yamcs");
            String id;
            if(yconf.containsKey(SERVER_ID_KEY)) {
                id = yconf.getString(SERVER_ID_KEY);
            } else {
                id = InetAddress.getLocalHost().getHostName();
            }
            serverId = id;
            staticlog.debug("Using serverId {}", serverId);
            return serverId;
        } catch (ConfigurationException e) {
            throw e;
        } catch (UnknownHostException e) {
            String msg = "Java cannot resolve local host (InetAddress.getLocalHost()). Make sure it's defined properly or alternatively add 'serverId: <name>' to yamcs.yaml";
            staticlog.warn(msg);
            throw new ConfigurationException(msg, e);
        }
    }

    private void loadTimeService() throws ConfigurationException, IOException {
        YConfiguration conf=YConfiguration.getConfiguration("yamcs."+instance);
        if(conf.containsKey("timeService")) {
            Map<String, Object> m = conf.getMap("timeService");
            String servclass = YConfiguration.getString(m, "class");            
            Object args = m.get("args");
            YObjectLoader<TimeService> objLoader = new YObjectLoader<TimeService>();
            if(args == null) {
                timeService = objLoader.loadObject(servclass, instance);
            } else {
                timeService = objLoader.loadObject(servclass, instance, args);
            }
        } else {
            timeService = new RealtimeTimeService();
        }
    }
    
    public static YamcsServer getInstance(String yamcsInstance) {     
        return instances.get(yamcsInstance);
    }

    public TimeService getTimeService() {
        return timeService;
    }
    
    public static void configureNonBlocking(SimpleString dataAddress) {
	//TODO
	//Object o=hornetServer.getHornetQServer().getManagementService().getResource(dataAddress.toString());
    }

    /**
     * @param args
     * @throws ConfigurationException
     * @throws IOException
     * @throws YProcessorException
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
            serverId = deriveServerId();
            setupSecurity();
            setupHttpServer();
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

    private static void setupSecurity() {
        org.yamcs.security.Privilege.getInstance();
    }


    private static void printOptionsAndExit() {
        System.err.println("Usage: yamcs-server.sh");
        System.err.println("\t All options are taken from yamcs.yaml");
        System.exit(-1);
    }

    public static TimeService getTimeService(String yamcsInstance) {
        if(instances.containsKey(yamcsInstance)) {
            return instances.get(yamcsInstance).getTimeService();
        } else {
            if(mockupTimeService!=null) {
                return mockupTimeService;
            } else {            
                return realtimeTimeService; //happens from unit tests
            }
        }
        
    }
    
    @SuppressWarnings("unchecked")
    public <T extends Service> T getService(Class<T> serviceClass) {
        for(Service s: serviceList) {
            if(serviceClass == s.getClass()) {
                return (T) s;
            }
        }
        return null;
    }


    public static <T extends Service> T getService(String yamcsInstance, Class<T> serviceClass) {
        YamcsServer ys = YamcsServer.getInstance(yamcsInstance);
        if(ys==null) return null;
        return ys.getService(serviceClass);
    }
    
    public static void setMockupTimeService(TimeService timeService) {
        mockupTimeService = timeService;
    }
}
