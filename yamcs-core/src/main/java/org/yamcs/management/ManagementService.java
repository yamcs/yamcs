package org.yamcs.management;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorFactory;
import org.yamcs.YProcessor;
import org.yamcs.ProcessorClient;
import org.yamcs.ProcessorException;
import org.yamcs.ProcessorListener;
import org.yamcs.YamcsException;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueListener;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.tctm.Link;
import org.yamcs.xtceproc.ProcessingStatistics;

import com.google.common.util.concurrent.Service;

/**
 * Responsible for integrating with core yamcs classes, encoding to protobuf,
 * and forwarding aggregated info downstream.
 * <p>
 * Notable examples of downstream listeners are the MBeanServer, the ActiveMQ-business,
 * and subscribed websocket clients.
 */
public class ManagementService implements ProcessorListener {
    
    final MBeanServer mbeanServer;

    final boolean jmxEnabled;
    static Logger log = LoggerFactory.getLogger(ManagementService.class.getName());
    final String tld="yamcs";
    static ManagementService managementService;

    Map<Integer, ClientControlImpl> clients = Collections.synchronizedMap(new HashMap<Integer, ClientControlImpl>());
    AtomicInteger clientId=new AtomicInteger();
    
    List<LinkControlImpl> links=new CopyOnWriteArrayList<LinkControlImpl>();
    List<CommandQueueManager> qmanagers=new CopyOnWriteArrayList<>();
    
    // Used to update TM-statistics, and Link State
    ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1);
    
    Set<ManagementListener> managementListeners = new CopyOnWriteArraySet<>(); // Processors & Clients. Should maybe split up
    Set<LinkListener> linkListeners = new CopyOnWriteArraySet<>();
    Set<CommandQueueListener> commandQueueListeners = new CopyOnWriteArraySet<>();

    // keep track of registered services
    Map<String, Integer> servicesCount = new HashMap<>();

    Map<YProcessor, Statistics> yprocs=new ConcurrentHashMap<YProcessor, Statistics>();
    static final Statistics STATS_NULL=Statistics.newBuilder().setInstance("null").setYProcessorName("null").build();//we use this one because ConcurrentHashMap does not support null values

    static public void setup(boolean jmxEnabled) throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, MalformedObjectNameException, NullPointerException {
        managementService = new ManagementService(jmxEnabled);
    }

    static public ManagementService getInstance() {
        return managementService;
    }

    private ManagementService(boolean jmxEnabled) throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, MalformedObjectNameException, NullPointerException {
        this.jmxEnabled=jmxEnabled;

        if(jmxEnabled)
            mbeanServer=ManagementFactory.getPlatformMBeanServer();
        else
            mbeanServer=null;

        YProcessor.addProcessorListener(this);
        timer.scheduleAtFixedRate(() -> updateStatistics(), 1, 1, TimeUnit.SECONDS);
        timer.scheduleAtFixedRate(() -> checkLinkUpdate(), 1, 1, TimeUnit.SECONDS);
    }

    public void shutdown() {
        managementListeners.clear();
    }

    public void registerService(String instance, String serviceName, Service service) {
        if(jmxEnabled) {
            ServiceControlImpl sci;
            try {
                sci = new ServiceControlImpl(service);

                // if a service with the same name has already been registered, suffix the service name with an index
                int serviceCount = 0;
                if(servicesCount.containsKey(serviceName)) {
                    serviceCount = servicesCount.get(serviceName);
                    servicesCount.remove(serviceName);
                }
                servicesCount.put(serviceName, ++serviceCount);
                if(serviceCount > 1)
                    serviceName=serviceName + "_" + serviceCount;

                // register service
                mbeanServer.registerMBean(sci, ObjectName.getInstance(tld+"."+instance+":type=services,name="+serviceName));

            } catch (Exception e) {
                log.warn("Got exception when registering a service", e);
            }
        }
    }

    public void unregisterService(String instance, String serviceName) {
        if(jmxEnabled) {
            try {

                // check if this serviceName has been registered several time
                int serviceCount = 0;
                String serviceName_  = serviceName;
                if(servicesCount.containsKey(serviceName) && (serviceCount = servicesCount.get(serviceName)) > 0)
                {
                    if(serviceCount > 1)
                        serviceName_ = serviceName + "_" + serviceCount;
                    serviceCount--;
                    servicesCount.replace(serviceName, serviceCount);
                }

                // unregister service
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+instance+":type=services,name="+serviceName_));
            } catch (Exception e) {
                log.warn("Got exception when unregistering a service", e);
            }
        }
    }


    public void registerLink(String instance, String name, String streamName, String spec, Link link) {
        try {
            LinkControlImpl lci = new LinkControlImpl(instance, name, streamName, spec, link);
            if(jmxEnabled) {
                mbeanServer.registerMBean(lci, ObjectName.getInstance(tld+"."+instance+":type=links,name="+name));
            }
            links.add(lci);
            linkListeners.forEach(l -> l.registerLink(lci.getLinkInfo()));
        } catch (Exception e) {
            log.warn("Got exception when registering a link: "+e, e);
        }
    }

    public void unregisterLink(String instance, String name) {
        if(jmxEnabled) {
            try {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+instance+":type=links,name="+name));
            } catch (Exception e) {
                log.warn("Got exception when unregistering a link", e);
            }
        }
        linkListeners.forEach(l -> l.unregisterLink(instance, name));
    }
    
    public CommandQueueManager getQueueManager(String instance, String processorName) throws YamcsException {
        for(int i=0;i<qmanagers.size();i++) {
            CommandQueueManager cqm=qmanagers.get(i);
            if(cqm.getInstance().equals(instance) && cqm.getChannelName().equals(processorName)) {
                return cqm;
            }
        }

        throw new YamcsException("Cannot find a command queue manager for "+instance+"/"+processorName);
    }
    
    public List<CommandQueueManager> getQueueManagers() {
        return qmanagers;
    }

    public void registerYProcessor(YProcessor yproc) {
        try {
            YProcessorControlImpl cci = new YProcessorControlImpl(yproc);
            if(jmxEnabled) {
                mbeanServer.registerMBean(cci, ObjectName.getInstance(tld+"."+yproc.getInstance()+":type=processors,name="+yproc.getName()));
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a processor", e);
        }
    }

    public void unregisterYProcessor(YProcessor yproc) {
        if(jmxEnabled) {
            try {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+yproc.getInstance()+":type=processors,name="+yproc.getName()));
            } catch (Exception e) {
                log.warn("Got exception when unregistering a processor", e);
            }
        }
    }

    public int registerClient(String instance, String yprocName,  ProcessorClient client) {
        int id=clientId.incrementAndGet();
        try {
            YProcessor c=YProcessor.getInstance(instance, yprocName);
            if(c==null) throw new YamcsException("Unexisting yprocessor ("+instance+", "+yprocName+") specified");
            ClientControlImpl cci = new ClientControlImpl(instance, id, client.getUsername(), client.getApplicationName(), yprocName, client);
            clients.put(cci.getClientInfo().getId(), cci);
            if(jmxEnabled) {
                mbeanServer.registerMBean(cci, ObjectName.getInstance(tld+"."+instance+":type=clients,processor="+yprocName+",id="+id));
            }
            managementListeners.forEach(l -> l.clientRegistered(cci.getClientInfo()));
        } catch (Exception e) {
            log.warn("Got exception when registering a client", e);
        }
        return id;
    }

    public void unregisterClient(int id) {
        ClientControlImpl cci=clients.remove(id);
        if(cci==null) return;
        ClientInfo ci=cci.getClientInfo();
        try {
            if(jmxEnabled) {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+ci.getInstance()+":type=clients,processor="+ci.getProcessorName()+",id="+id));
            }
            managementListeners.forEach(l -> l.clientUnregistered(ci));
        } catch (Exception e) {
            log.warn("Got exception when registering a client", e);
        }
    }

    private void switchProcessor(ClientControlImpl cci, YProcessor yproc, AuthenticationToken authToken) throws ProcessorException {
        ClientInfo oldci=cci.getClientInfo();
        cci.switchYProcessor(yproc, authToken);
        ClientInfo ci=cci.getClientInfo();

        try {
            if(jmxEnabled) {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+oldci.getInstance()+":type=clients,processor="+oldci.getProcessorName()+",id="+ci.getId()));
                mbeanServer.registerMBean(cci, ObjectName.getInstance(tld+"."+ci.getInstance()+":type=clients,processor="+ci.getProcessorName()+",id="+ci.getId()));
            }
            managementListeners.forEach(l -> l.clientInfoChanged(ci));
        } catch (Exception e) {
            log.warn("Got exception when switching processor", e);
        }

    }

    public void createProcessor(ProcessorManagementRequest cr, AuthenticationToken authToken) throws YamcsException{
        log.info("Creating new processor instance="+cr.getInstance()+" name="+cr.getName()+" type="+cr.getType()+" spec="+cr.getSpec()+"' persistent="+cr.getPersistent());
        
        String username;
        if (authToken != null && authToken.getPrincipal() != null) {
            username = authToken.getPrincipal().toString();
        } else {
            username = Privilege.getDefaultUser();
        }
        if(!Privilege.getInstance().hasPrivilege(authToken, Privilege.Type.SYSTEM, Privilege.SystemPrivilege.MayControlProcessor)) {
            if(cr.getPersistent()) {
                log.warn("User "+username+" is not allowed to create persistent processors");
                throw new YamcsException("Permission denied");
            }
            if(!"Archive".equals(cr.getType())) {
                log.warn("User "+username+" is not allowed to create processors of type "+cr.getType());
                throw new YamcsException("Permission denied");
            }
            for(int i=0;i<cr.getClientIdCount();i++) {
                ClientInfo si=clients.get(cr.getClientId(i)).getClientInfo();
                if(!username.equals(si.getUsername())) {
                    log.warn("User "+username+" is not allowed to connect "+si.getUsername()+" to new processor "+cr.getName() );
                    throw new YamcsException("Permission denied");
                }
            }
        }

        YProcessor yproc;
        try {
            int n=0;
            
            Object spec;
            if(cr.hasReplaySpec()) {
                spec = cr.getReplaySpec();
            } else {
                spec = cr.getSpec();
            }
            yproc = ProcessorFactory.create(cr.getInstance(), cr.getName(), cr.getType(), username, spec);
            yproc.setPersistent(cr.getPersistent());
            for(int i=0;i<cr.getClientIdCount();i++) {
                ClientControlImpl cci=clients.get(cr.getClientId(i));
                if(cci!=null) {
                    switchProcessor(cci, yproc, authToken);
                    n++;
                } else {
                    log.warn("createProcessor called with invalid client id:"+cr.getClientId(i)+"; ignored.");
                }
            }
            if(n>0 || cr.getPersistent()) {
                log.info("Starting new processor '" + yproc.getName() + "' with " + yproc.getConnectedClients() + " clients");
                yproc.startAsync();
                yproc.awaitRunning();
            } else {
                yproc.quit();
                throw new YamcsException("createProcessor invoked with a list full of invalid client ids");
            }
        } catch (ProcessorException | ConfigurationException e) {
            throw new YamcsException(e.getMessage(), e.getCause());
        } catch (IllegalStateException e1) {
            Throwable t =  e1.getCause();
            if(t instanceof YamcsException) {
                throw (YamcsException )t;
            } else {
                throw new YamcsException(t.getMessage(), t.getCause());
            }
        }
    }


    public void connectToProcessor(ProcessorManagementRequest cr, AuthenticationToken usertoken) throws YamcsException {
        YProcessor chan=YProcessor.getInstance(cr.getInstance(), cr.getName());
        if(chan==null) throw new YamcsException("Unexisting processor "+cr.getInstance()+"/"+cr.getName()+" specified");


        String username;
        if  (usertoken != null && usertoken.getPrincipal() != null) {
            username = usertoken.getPrincipal().toString();
        } else {
            username = Privilege.getDefaultUser();
        }
        log.debug("User "+ username+" wants to connect clients "+cr.getClientIdList()+" to processor "+cr.getName());


        if(!Privilege.getInstance().hasPrivilege(usertoken, Privilege.Type.SYSTEM, "MayControlProcessor") &&
                !((chan.isPersistent() || chan.getCreator().equals(username)))) {
            log.warn("User "+username+" is not allowed to connect users to processor "+cr.getName() );
            throw new YamcsException("permission denied");
        }
        if(!Privilege.getInstance().hasPrivilege(usertoken, Privilege.Type.SYSTEM, "MayControlProcessor")) {
            for(int i=0; i<cr.getClientIdCount(); i++) {
                ClientInfo si=clients.get(cr.getClientId(i)).getClientInfo();
                if(!username.equals(si.getUsername())) {
                    log.warn("User "+username+" is not allowed to connect "+si.getUsername()+" to processor "+cr.getName());
                    throw new YamcsException("Permission denied");
                }
            }
        }

        try {
            for(int i=0;i<cr.getClientIdCount();i++) {
                int id=cr.getClientId(i);
                ClientControlImpl cci=clients.get(id);
                switchProcessor(cci, chan, usertoken);
            }
        } catch(ProcessorException e) {
            throw new YamcsException(e.toString());
        }
    }

    public void registerCommandQueueManager(String instance, String yprocName, CommandQueueManager cqm) {
        try {
            for(CommandQueue cq:cqm.getQueues()) {
                if(jmxEnabled) {
                    CommandQueueControlImpl cqci = new CommandQueueControlImpl(instance, yprocName, cqm, cq);
                    mbeanServer.registerMBean(cqci, ObjectName.getInstance(tld+"."+instance+":type=commandQueues,processor="+yprocName+",name="+cq.getName()));
                }
            }
            qmanagers.add(cqm);
            for (CommandQueueListener l : commandQueueListeners) {
                cqm.registerListener(l);
                for(CommandQueue q:cqm.getQueues()) {
                    l.updateQueue(q);
                }
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a command queue", e);
        }
    }
    
    public List<CommandQueueManager> getCommandQueueManagers() {
        return qmanagers;
    }
    
    public CommandQueueManager getCommandQueueManager(YProcessor processor) {
        for (CommandQueueManager mgr : qmanagers) {
            if (mgr.getInstance().equals(processor.getInstance())
                    && mgr.getChannelName().equals(processor.getName())) {
                return mgr;
            }
        }
        return null;
    }
    
    public void enableLink(String instance, String name) throws YamcsException {
        log.debug("received enableLink for "+instance+"/"+name);
        boolean found=false;
        for(int i=0;i<links.size();i++) {
            LinkControlImpl lci=links.get(i);
            LinkInfo li2=lci.getLinkInfo();
            if(li2.getInstance().equals(instance) && li2.getName().equals(name)) {
                found=true;
                lci.enable();
                break;
            }
        }
        if(!found) throw new YamcsException("There is no link named '"+name+"' in instance "+instance);
    }
    
    public void disableLink(String instance, String name) throws YamcsException {
        log.debug("received disableLink for "+instance+"/"+name);
        boolean found=false;
        for(int i=0;i<links.size();i++) {
            LinkControlImpl lci=links.get(i);
            LinkInfo li2=lci.getLinkInfo();
            if(li2.getInstance().equals(instance) && li2.getName().equals(name)) {
                found=true;
                lci.disable();
                break;
            }
        }
        if(!found) throw new YamcsException("There is no link named '"+name+"' in instance "+instance);
    }
    
    /**
     * Adds a listener that is to be notified when any processor, or any client
     * is updated. Calling this multiple times has no extra effects. Either you
     * listen, or you don't.
     */
    public boolean addManagementListener(ManagementListener l) {
        return managementListeners.add(l);
    }
    
    /**
     * Adds a listener that is to be notified when any processor, or any client
     * is updated. Calling this multiple times has no extra effects. Either you
     * listen, or you don't.
     */
    public boolean addLinkListener(LinkListener l) {
        return linkListeners.add(l);
    }
    
    public boolean removeManagementListener(ManagementListener l) {
        return managementListeners.remove(l);
    }
    
    public boolean addCommandQueueListener(CommandQueueListener l) {
        return commandQueueListeners.add(l);
    }
    
    public boolean removeCommandQueueListener(CommandQueueListener l) {
        boolean removed = commandQueueListeners.remove(l);
        qmanagers.forEach(m -> m.removeListener(l));
        return removed;
    }
    
    public boolean removeLinkListener(LinkListener l) {
        return linkListeners.remove(l);
    }
    
    public List<LinkInfo> getLinkInfo() {
        List<LinkInfo> l = new ArrayList<>();
        for (LinkControlImpl li : links) {
            l.add(li.getLinkInfo());
        }
        return l;
    }
    
    public LinkInfo getLinkInfo(String instance, String name) {
        for(int i=0;i<links.size();i++) {
            LinkControlImpl lci=links.get(i);
            LinkInfo li=lci.getLinkInfo();
            if(li.getInstance().equals(instance) && li.getName().equals(name)) {
                return li;
            }
        }
        return null;
    }

    public Set<ClientInfo> getClientInfo() {
        synchronized(clients) {
            return clients.values().stream()
                    .map(v -> v.getClientInfo())
                    .collect(Collectors.toSet());
        }
    }
    
    public Set<ClientInfo> getClientInfo(String username) {
        synchronized(clients) {
            return clients.values().stream()
                    .map(v -> v.getClientInfo())
                    .filter(c -> c.getUsername().equals(username))
                    .collect(Collectors.toSet());
        }
    }
    
    public ClientInfo getClientInfo(int clientId) {
        ClientControlImpl cci = clients.get(clientId);
        if(cci==null) return null;
        return cci.getClientInfo();
    }
    
    private void updateStatistics() {
        try {
            for(Entry<YProcessor,Statistics> entry:yprocs.entrySet()) {
                YProcessor yproc=entry.getKey();
                Statistics stats=entry.getValue();
                ProcessingStatistics ps=yproc.getTmProcessor().getStatistics();
                if((stats==STATS_NULL) || (ps.getLastUpdated()>stats.getLastUpdated())) {
                    stats=ManagementGpbHelper.buildStats(yproc);
                    yprocs.put(yproc, stats);
                }
                if(stats!=STATS_NULL) {
                    for (ManagementListener l : managementListeners) {
                        l.statisticsUpdated(yproc, stats);   
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void checkLinkUpdate() {
        // see if any link has changed
        for(LinkControlImpl lci:links) {
            if(lci.hasChanged()) {
                LinkInfo li = lci.getLinkInfo();
                linkListeners.forEach(l -> l.linkChanged(li));
            }
        }
    }

    @Override
    public void processorAdded(YProcessor processor) {
        ProcessorInfo pi = ManagementGpbHelper.toProcessorInfo(processor);
        managementListeners.forEach(l -> l.processorAdded(pi));
        yprocs.put(processor, STATS_NULL);
    }

    @Override
    public void processorClosed(YProcessor processor) {
        ProcessorInfo pi = ManagementGpbHelper.toProcessorInfo(processor);
        managementListeners.forEach(l -> l.processorClosed(pi));
        yprocs.remove(processor);
    }

    @Override
    public void processorStateChanged(YProcessor processor) {
        ProcessorInfo pi = ManagementGpbHelper.toProcessorInfo(processor);
        managementListeners.forEach(l -> l.processorStateChanged(pi));
    }
}
