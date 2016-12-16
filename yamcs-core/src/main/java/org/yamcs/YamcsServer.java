package org.yamcs;


import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.YamcsApiException;
import org.yamcs.archive.ReplayServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.MissionDatabase;
import org.yamcs.protobuf.YamcsManagement.ServiceInfo;
import org.yamcs.protobuf.YamcsManagement.ServiceState;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.protobuf.YamcsManagement.YamcsInstances;
import org.yamcs.time.RealtimeTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.management.JMXService;
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
    static Map<String, YamcsServer> instances=new LinkedHashMap<>();
    final static private String SERVER_ID_KEY="serverId";

    String instance;
    ReplayServer replay;
    //instance specific services
    List<ServiceWithConfig> serviceList;

    //global services
    static List<ServiceWithConfig> globalServiceList;
    Logger log;
    static Logger staticlog = LoggerFactory.getLogger(YamcsServer.class);

    /**in the shutdown, allow services this number of seconds for stopping*/
    public static int SERVICE_STOP_GRACE_TIME = 10;
    TimeService timeService;

    static TimeService realtimeTimeService = new RealtimeTimeService();

    //used for unit tests
    static TimeService mockupTimeService;

    static private String serverId;
    static YObjectLoader<Service> objLoader = new YObjectLoader<>();

    static CrashHandler globalCrashHandler;
    private CrashHandler crashHandler;

    YamcsServer(String instance) throws IOException, StreamSqlException, ParseException, YamcsApiException {
        this.instance = instance;

        //TODO - fix bootstrap issue
        instances.put(instance, this);

        log = LoggingUtils.getLogger(YamcsServer.class, instance);

        YConfiguration conf = YConfiguration.getConfiguration("yamcs."+instance);
        loadTimeService();

        ManagementService managementService = ManagementService.getInstance();
        StreamInitializer.createStreams(instance);
        YProcessor.addProcessorListener(managementService);
        if(conf.containsKey("crashHandler")) {
            crashHandler = loadCrashHandler(conf);
        } else {
            crashHandler = globalCrashHandler;
        }
        List<Object> services = conf.getList("services");
        serviceList = createServices(instance, services);
        startServices(serviceList);

    }

    private static CrashHandler loadCrashHandler( YConfiguration conf) throws ConfigurationException, IOException {
        YObjectLoader<CrashHandler> loader = new YObjectLoader<>();
        if(conf.containsKey("crashHandler", "args")) {
            return loader.loadObject(conf.getString("crashHandler", "class"), conf.getMap("crashHandler", "args"));
        } else {
            return loader.loadObject(conf.getString("crashHandler", "class"));
        }
    }
    /**
     * Creates services either server-wide (if instance is null) or instance-specific.
     * The services are not yet started. This must be done in a second step, so
     * that components can ask YamcsServer for other service instantiations.
     *
     * @param services - list of service configuration; each of them is a string (=classname) or a map
     * @param instance - if null, then start a server-wide service, otherwise an instance-specific service
     * @throws IOException
     * @throws ConfigurationException
     */
    @SuppressWarnings("unchecked")
    private static List<ServiceWithConfig> createServices(String instance, List<Object> servicesConfig) throws ConfigurationException, IOException {
        ManagementService managementService = ManagementService.getInstance();
        List<ServiceWithConfig> serviceList = new CopyOnWriteArrayList<>();
        for(Object servobj:servicesConfig) {
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
            staticlog.info("Loading {} service {}", (instance==null)?"server-wide":instance, servclass);
            ServiceWithConfig swc;
            try {
                swc = createService(instance, servclass, servclass, args);
                serviceList.add(swc);
            } catch (NoClassDefFoundError e) {
                staticlog.error("Cannot create service {}, with arguments {}: class {} not found", servclass, args, e.getMessage());
                throw e;
            } catch (Throwable t) {
                staticlog.error("Cannot create service {}, with arguments {}: {}", servclass, args, t.getMessage());
                throw t;
            }  
           
            managementService.registerService(instance, servclass, swc.service);
        }

        return serviceList;
    }

    /**
     * Registers an instance-specific service and starts it up
     */
    public void createAndStartService(String serviceClass, Map<String, Object> args) throws ConfigurationException, IOException {
        Map<String, Object> serviceConf = new HashMap<>(2);
        serviceConf.put("class", serviceClass);
        serviceConf.put("args", args);
        List<ServiceWithConfig> newServices = createServices(instance, Arrays.asList(serviceConf));
        serviceList.addAll(newServices);
        startServices(newServices);
    }

    /**
     * Starts the specified list of services.
     *
     * @param serviceList list of service configurations
     * @throws ConfigurationException
     */
    public static void startServices(List<ServiceWithConfig> serviceList) throws ConfigurationException {
        for(ServiceWithConfig swc:serviceList) {
            swc.service.startAsync();
            try {
                swc.service.awaitRunning();
            } catch (IllegalStateException e) {
                //this happens when it fails, the next check will throw an error in this case
            }
            State result = swc.service.state();
            if(result==State.FAILED) {
                throw new ConfigurationException("Failed to start service "+swc.service, swc.service.failureCause());
            }
        }
    }

    public static void shutDown() throws Exception {
        for(YamcsServer ys: instances.values()) {
            ys.stop();
        }
    }

    public void stop() {
        for(int i = serviceList.size()-1; i>=0; i--) {
            ServiceWithConfig swc = serviceList.get(i);
            Service s = swc.service;
            s.stopAsync();
            try {
                s.awaitTerminated(SERVICE_STOP_GRACE_TIME, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.error("Service "+s.getClass().getName()+" did not stop in "+SERVICE_STOP_GRACE_TIME + " seconds");
            } catch (IllegalStateException e) {
                log.error("Service "+s.getClass().getName()+" was in a bad state: {}", e.getMessage());
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
        YConfiguration c = YConfiguration.getConfiguration("yamcs");
        if(c.containsKey("crashHandler")) {
            globalCrashHandler = loadCrashHandler(c);
        } else {
            globalCrashHandler = new LogCrashHandler();
        }


        if(c.containsKey("services")) {
            List<Object> services=c.getList("services");
            globalServiceList = createServices(null, services);
            startServices(globalServiceList);
        }


        if (c.containsKey("instances")) {
            List<String> instArray = c.getList("instances");
            for(String inst:instArray) {
                createYamcsInstance(inst);
            }
        }

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable thrown) {
                String msg = "Uncaught exception '"+thrown+"' in thread "+t+": "+Arrays.toString(thrown.getStackTrace());
                staticlog.error(msg);
                globalCrashHandler.handleCrash("UncaughtException", msg);
            }
        });

        if(System.getenv("YAMCS_DAEMON")==null) {
            staticlog.info("Server running... press ctrl-c to stop");
        } else {//the init.d/yamcs-server depends on this line on the standard output, do not change it (without changing the script also)!
            System.out.println("yamcsstartup success");
        }
    }

    public static void createYamcsInstance(String name) throws IOException, StreamSqlException, ParseException, YamcsApiException {
        staticlog.info("Loading instance '" + name + "'");
        if (instances.containsKey(name)) {
            throw new ConfigurationException(String.format("There already exists an instance named '%s'", name));
        }
        instances.put(name, new YamcsServer(name));
    }

    public static Set<String> getYamcsInstanceNames() {
        return instances.keySet();
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
            if (!c.isList("mdb")) {
                String configName = c.getString("mdb");
                mdb.setConfigName(configName);
            }
            XtceDb xtcedb=XtceDbFactory.getInstance(name);
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
            YObjectLoader<TimeService> objLoader = new YObjectLoader<>();
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

    /**
     * @param args
     */
    public static void main(String[] args) {
        if(args.length>0) printOptionsAndExit();

        try {
            YConfiguration.setup();
            serverId = deriveServerId();
            setupSecurity();
            JMXService.setup(true);
            ManagementService.setup(true);

            setupYamcsServer();

        } catch (ConfigurationException e) {
            staticlog.error("Could not start Yamcs Server", e);
            System.err.println(e.toString());
            System.exit(-1);
        } catch (Throwable e) {
            staticlog.error("Could not start Yamcs Server", e);
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

    public List<ServiceInfo> getServices() {
        return getServiceInfo(instance, serviceList);
    }

    public static  List<ServiceInfo> getGlobalServices() {
        return getServiceInfo(null, globalServiceList);
    }

    private static List<ServiceInfo> getServiceInfo(String instance, List<ServiceWithConfig> serviceList) {
        List<ServiceInfo> r = new ArrayList<>(serviceList.size());
        for(ServiceWithConfig swc: serviceList) {
            ServiceInfo.Builder sib = ServiceInfo.newBuilder().setName(swc.name).setClassName(swc.serviceClass).setState(ServiceState.valueOf(swc.service.state().name()));
            if(instance!=null) sib.setInstance(instance);
            r.add(sib.build());
        }
        return r;
    }

    public static <T extends Service> T getService(String yamcsInstance, Class<T> serviceClass) {
        YamcsServer ys = YamcsServer.getInstance(yamcsInstance);
        if(ys==null) return null;
        return ys.getService(serviceClass);
    }

    public static void setMockupTimeService(TimeService timeService) {
        mockupTimeService = timeService;
    }

    public Service getService(String serviceName) {
        if(serviceList==null) return null;

        for(ServiceWithConfig swc: serviceList) {
            Service s = swc.service;
            if(s.getClass().getName().equals(serviceName)) {
                return s;
            }
        }
        return null;
    }


    @SuppressWarnings("unchecked")
    public <T extends Service> T getService(Class<T> serviceClass) {
        return (T) getService(serviceClass.getName());
    }

    public static Service getGlobalService(String serviceName) {
        if(globalServiceList==null) return null;

        synchronized(globalServiceList) {
            for(ServiceWithConfig swc: globalServiceList) {
                Service s = swc.service;
                if(s.getClass().getName().equals(serviceName)) {
                    return s;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Service> T getGlobalService(Class<T> serviceClass) {
        return (T) getGlobalService(serviceClass.getName());
    }

    static private ServiceWithConfig createService(String instance, String serviceClass, String serviceName, Object args) throws ConfigurationException, IOException {
        Service serv;
        if(instance!=null) {
            if(args == null) serv = objLoader.loadObject(serviceClass, instance);
            else serv = objLoader.loadObject(serviceClass, instance, args);
        } else {
            if(args == null) serv = objLoader.loadObject(serviceClass);
            else serv = objLoader.loadObject(serviceClass, args);
        }
        return new ServiceWithConfig(serv, serviceClass, serviceName, args);
    }



    //starts a service that has stopped or not yet started
    private static Service startService(String instance, String serviceName, List<ServiceWithConfig> serviceList) throws ConfigurationException, IOException {
        for(int i=0; i<serviceList.size(); i++) {
            ServiceWithConfig swc = serviceList.get(i);
            if(swc.name.equals(serviceName)) {
                switch(swc.service.state()) {
                case RUNNING:
                case STARTING:
                    //do nothing, service is already starting
                    break;
                case NEW: //not yet started, start it now
                    swc.service.startAsync();
                    break;
                case FAILED:
                case STOPPING:
                case TERMINATED:
                    //start a new one
                    swc = createService(instance, swc.serviceClass, serviceName, swc.args);
                    serviceList.set(i, swc);
                    swc.service.startAsync();
                    break;
                }
                return swc.service;
            }
        }
        return null;
    }
    public static void startGlobalService(String serviceName) throws ConfigurationException, IOException {
        startService(null, serviceName, globalServiceList);
    }

    public void startService(String serviceName) throws ConfigurationException, IOException {
        startService(instance, serviceName, serviceList);
    }


    private static class ServiceWithConfig {
        final Service service;
        final String serviceClass;
        final String name;
        final Object args;

        public ServiceWithConfig(Service service, String serviceClass, String name,  Object args) {
            super();
            this.service = service;
            this.serviceClass = serviceClass;
            this.name = name;
            this.args = args;
        }

    }

    public static CrashHandler getCrashHandler(String yamcsInstance) {
        YamcsServer ys = getInstance(yamcsInstance);
        if(ys!=null) {
            return ys.getCrashHandler();
        } else {
            return globalCrashHandler; //may happen if the instance name is not valid (in unit tests)
        }
    }

    private CrashHandler getCrashHandler() {
        return crashHandler;
    }
}
