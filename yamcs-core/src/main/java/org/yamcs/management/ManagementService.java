package org.yamcs.management;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorClient;
import org.yamcs.YProcessorException;
import org.yamcs.YProcFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.Privilege;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.tctm.Link;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;


import com.google.common.util.concurrent.Service;
import org.yamcs.YamcsException;
import org.yamcs.protobuf.YamcsManagement.ProcessorRequest;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;

public class ManagementService {
    final MBeanServer mbeanServer;
    HornetManagement hornetMgr;
    HornetProcessorManagement hornetYProcMgr;
    HornetCommandQueueManagement hornetCmdQueueMgr;

    final boolean jmxEnabled, hornetEnabled;
    static Logger log=LoggerFactory.getLogger(ManagementService.class.getName());
    final String tld="yamcs";
    static ManagementService managementService;

    Map<Integer, ClientControlImpl> clients=Collections.synchronizedMap(new HashMap<Integer, ClientControlImpl>());
    AtomicInteger clientId=new AtomicInteger();

    static public void setup(boolean hornetEnabled, boolean jmxEnabled) throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, MalformedObjectNameException, NullPointerException {
        managementService=new ManagementService(hornetEnabled, jmxEnabled);
    }

    static public ManagementService getInstance() {
        return managementService;
    }

    private ManagementService(boolean hornetEnabled, boolean jmxEnabled) throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException, MalformedObjectNameException, NullPointerException {
        this.hornetEnabled=hornetEnabled;
        this.jmxEnabled=jmxEnabled;

        if(jmxEnabled)
            mbeanServer=ManagementFactory.getPlatformMBeanServer();
        else 
            mbeanServer=null;

        if(hornetEnabled) {
            try {
                ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1);
                hornetMgr=new HornetManagement(this, timer);
                hornetCmdQueueMgr=new HornetCommandQueueManagement();
                hornetYProcMgr=new HornetProcessorManagement(this, timer);
                YProcessor.addYProcListener(hornetYProcMgr);
            } catch (Exception e) {
                log.error("failed to start hornet management service: ", e);
                hornetEnabled=false;
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        if(hornetEnabled) {
            YProcessor.removeYProcListner(hornetYProcMgr);
            hornetMgr.stop();
            hornetCmdQueueMgr.stop();
            hornetYProcMgr.close();
        }
    }

    public void registerService(String instance, String serviceName, Service service) {
        if(jmxEnabled) {
            ServiceControlImpl sci;
            try {
                sci = new ServiceControlImpl(service);
                mbeanServer.registerMBean(sci, ObjectName.getInstance(tld+"."+instance+":type=services,name="+serviceName));
            } catch (Exception e) {
                log.warn("Got exception when registering a service", e);
            }
        }
    }

    public void unregisterService(String instance, String serviceName) {
        if(jmxEnabled) {
            try {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+instance+":type=services,name="+serviceName));
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
            if(hornetEnabled) {
                hornetMgr.registerLink(instance, lci);
            }
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

        if(hornetEnabled) {
            hornetMgr.unRegisterLink(instance, name);
        }
    }


    public void registerYProcessor(YProcessor yproc) {
        try {
            YProcessorControlImpl cci = new YProcessorControlImpl(yproc);
            if(jmxEnabled) {
                mbeanServer.registerMBean(cci, ObjectName.getInstance(tld+"."+yproc.getInstance()+":type=yprocs,name="+yproc.getName()));
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a yprocessor", e);
        }
    }

    public void unregisterYProcessor(YProcessor yproc) {
        if(jmxEnabled) {
            try {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+yproc.getInstance()+":type=yprocs,name="+yproc.getName()));
            } catch (Exception e) {
                log.warn("Got exception when unregistering a yprocessor", e);
            }
        }
    }

    public int registerClient(String instance, String yprocName,  YProcessorClient client) {
        int id=clientId.incrementAndGet();
        try {
            YProcessor c=YProcessor.getInstance(instance, yprocName);
            if(c==null) throw new YamcsException("Unexisting yprocessor ("+instance+", "+yprocName+") specified");
            ClientControlImpl cci = new ClientControlImpl(instance, id, client.getUsername(), client.getApplicationName(), yprocName, client);
            clients.put(cci.getClientInfo().getId(), cci);
            if(jmxEnabled) {
                mbeanServer.registerMBean(cci, ObjectName.getInstance(tld+"."+instance+":type=clients,yproc="+yprocName+",id="+id));
            }
            if(hornetEnabled) {
                hornetYProcMgr.registerClient(cci.getClientInfo());
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a yproc", e);
        }
        return id;
    }



    public void unregisterClient(int id ) {
        ClientControlImpl cci=clients.remove(id);
        if(cci==null) return;
        ClientInfo ci=cci.getClientInfo();
        try {
            if(jmxEnabled) {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+ci.getInstance()+":type=clients,yproc="+ci.getProcessorName()+",id="+id));
            }
            if(hornetEnabled) {
                hornetYProcMgr.unregisterClient(ci);
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a yproc", e);
        }
    }

    private void switchYProcessor(ClientControlImpl cci, YProcessor yproc) throws YProcessorException {
        ClientInfo oldci=cci.getClientInfo();
        cci.switchYProcessor(yproc);
        ClientInfo ci=cci.getClientInfo();

        try {
            if(jmxEnabled) {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+oldci.getInstance()+":type=clients,yproc="+oldci.getProcessorName()+",id="+ci.getId()));
                mbeanServer.registerMBean(cci, ObjectName.getInstance(tld+"."+ci.getInstance()+":type=clients,yproc="+ci.getProcessorName()+",id="+ci.getId()));
            }
            if(hornetEnabled) {
                hornetYProcMgr.clientInfoChanged(ci);
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a yprocessor", e);
        }

    }

    public void createProcessor(ProcessorRequest cr, Privilege priv) throws YamcsException{
        log.info("Creating a new yproc instance="+cr.getInstance()+" name="+cr.getName()+" type="+cr.getType()+" spec="+cr.getSpec()+"' persistent="+cr.getPersistent());
        String currentUser=priv.getCurrentUser();
        if(currentUser==null) currentUser="unknown";

        if(!priv.hasPrivilege(Privilege.Type.SYSTEM, "MayControlYProcessor")) {
            if(cr.getPersistent()) { 
                log.warn("User "+currentUser+" is not allowed to create persistent yprocessors");
                throw new YamcsException("permission denied");
            }
            if(!"Archive".equals(cr.getType())) {
                log.warn("User "+currentUser+" is not allowed to create yprocessors of type "+cr.getType());
                throw new YamcsException("permission denied");
            }
            for(int i=0;i<cr.getClientIdCount();i++) {
                ClientInfo si=clients.get(cr.getClientId(i)).getClientInfo();
                if(!currentUser.equals(si.getUsername())) {
                    log.warn("User "+currentUser+" is not allowed to connect "+si.getUsername()+" to a new yprocessor "+cr.getName() );
                    throw new YamcsException("permission denied");
                }
            }
        }


        try {
            int n=0;
            YProcessor yproc;
            yproc = YProcFactory.create(cr.getInstance(), cr.getName(), cr.getType(), cr.getSpec(),"unknown");
            yproc.setPersistent(cr.getPersistent());
            for(int i=0;i<cr.getClientIdCount();i++) {
                ClientControlImpl cci=clients.get(cr.getClientId(i));
                if(cci!=null) {
                    switchYProcessor(cci, yproc);
                    n++;
                } else {
                    log.warn("createYProcessor called with invalid client id:"+cr.getClientId(i)+"; ignored.");
                }
            }
            if(n>0 || cr.getPersistent()) {
                log.info("starting new yprocessor'" + yproc.getName() + "' with " + yproc.getConnectedClients() + " clients");
                yproc.start();
            } else {
                yproc.quit();
                throw new YamcsException("createYProcessor invoked with a list full of invalid client ids");
            }
        } catch (YProcessorException e) {
            throw new YamcsException(e.getMessage(), e.getCause());
        } catch (ConfigurationException e) {
            e.printStackTrace();
            throw new YamcsException(e.getMessage(), e.getCause());
        }
    }


    public void connectToProcessor(ProcessorRequest cr, Privilege priv) throws YamcsException {
        YProcessor chan=YProcessor.getInstance(cr.getInstance(), cr.getName());
        if(chan==null) throw new YamcsException("Unexisting yproc ("+cr.getInstance()+", "+cr.getName()+") specified");


        String currentUser=priv.getCurrentUser();
        if(currentUser==null) currentUser="unknown";

        log.debug("User "+ currentUser+" wants to connect clients "+cr.getClientIdList()+" to yproc "+cr.getName());


        if(!priv.hasPrivilege(Privilege.Type.SYSTEM, "MayControlYProcessor") &&
                !((chan.isPersistent() || chan.getCreator().equals(currentUser)))) {
            log.warn("User "+currentUser+" is not allowed to connect users to yproc "+cr.getName() );
            throw new YamcsException("permission denied");
        }
        if(!priv.hasPrivilege(Privilege.Type.SYSTEM, "MayControlYProcessor")) {
            for(int i=0; i<cr.getClientIdCount(); i++) {
                ClientInfo si=clients.get(cr.getClientId(i)).getClientInfo();
                if(!currentUser.equals(si.getUsername())) {
                    log.warn("User "+currentUser+" is not allowed to connect "+si.getUsername()+" to yprocessor "+cr.getName());
                    throw new YamcsException("permission denied");
                }
            }
        }

        try {
            for(int i=0;i<cr.getClientIdCount();i++) {
                int id=cr.getClientId(i);
                ClientControlImpl cci=clients.get(id);
                switchYProcessor(cci, chan);
            }
        } catch(YProcessorException e) {
            throw new YamcsException(e.toString());
        }
    }

    public void registerCommandQueueManager(String instance, String yprocName, CommandQueueManager cqm) {
        try {
            for(CommandQueue cq:cqm.getQueues()) {
                if(jmxEnabled) {
                    CommandQueueControlImpl cqci = new CommandQueueControlImpl(instance, yprocName, cqm, cq);
                    mbeanServer.registerMBean(cqci, ObjectName.getInstance(tld+"."+instance+":type=commandQueues,yproc="+yprocName+",name="+cq.getName()));
                }
            }
            if(hornetEnabled) {
                hornetCmdQueueMgr.registerCommandQueueManager(cqm);
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a command queue", e);
        }
    }

    public ClientInfo getClientInfo(int clientId) {
        return clients.get(clientId).getClientInfo();
    }
}
