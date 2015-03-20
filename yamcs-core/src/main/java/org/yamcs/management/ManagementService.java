package org.yamcs.management;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ChannelClient;
import org.yamcs.ChannelException;
import org.yamcs.ChannelFactory;
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
import org.yamcs.protobuf.YamcsManagement.ChannelRequest;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;

public class ManagementService {
    final MBeanServer mbeanServer;
    HornetManagement hornetMgr;
    HornetChannelManagement hornetChannelMgr;
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
                hornetChannelMgr=new HornetChannelManagement(this, timer);
                Channel.addChannelListener(hornetChannelMgr);
            } catch (Exception e) {
                log.error("failed to start hornet management service: ", e);
                hornetEnabled=false;
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
	if(hornetEnabled) {
	    Channel.removeChannelListner(hornetChannelMgr);
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


    public void registerChannel(Channel channel) {
        try {
            ChannelControlImpl cci = new ChannelControlImpl(channel);
            if(jmxEnabled) {
                mbeanServer.registerMBean(cci, ObjectName.getInstance(tld+"."+channel.getInstance()+":type=channels,name="+channel.getName()));
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a channel", e);
        }
    }

    public void unregisterChannel(Channel channel) {
        if(jmxEnabled) {
            try {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+channel.getInstance()+":type=channels,name="+channel.getName()));
            } catch (Exception e) {
                log.warn("Got exception when unregistering a channel", e);
            }
        }
    }

    public int registerClient(String instance, String channelName,  ChannelClient client) {
        int id=clientId.incrementAndGet();
        try {
            Channel c=Channel.getInstance(instance, channelName);
            if(c==null) throw new YamcsException("Unexisting channel ("+instance+", "+channelName+") specified");
            ClientControlImpl cci = new ClientControlImpl(instance, id, client.getUsername(), client.getApplicationName(), channelName, client);
            clients.put(cci.getClientInfo().getId(), cci);
            if(jmxEnabled) {
                mbeanServer.registerMBean(cci, ObjectName.getInstance(tld+"."+instance+":type=clients,channel="+channelName+",id="+id));
            }
            if(hornetEnabled) {
                hornetChannelMgr.registerClient(cci.getClientInfo());
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a channel", e);
        }
        return id;
    }



    public void unregisterClient(int id ) {
        ClientControlImpl cci=clients.remove(id);
        if(cci==null) return;
        ClientInfo ci=cci.getClientInfo();
        try {
            if(jmxEnabled) {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+ci.getInstance()+":type=clients,channel="+ci.getChannelName()+",id="+id));
            }
            if(hornetEnabled) {
                hornetChannelMgr.unregisterClient(ci);
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a channel", e);
        }
    }

    private void switchChannel(ClientControlImpl cci, Channel chan) throws ChannelException {
        ClientInfo oldci=cci.getClientInfo();
        cci.switchChannel(chan);
        ClientInfo ci=cci.getClientInfo();

        try {
            if(jmxEnabled) {
                mbeanServer.unregisterMBean(ObjectName.getInstance(tld+"."+oldci.getInstance()+":type=clients,channel="+oldci.getChannelName()+",id="+ci.getId()));
                mbeanServer.registerMBean(cci, ObjectName.getInstance(tld+"."+ci.getInstance()+":type=clients,channel="+ci.getChannelName()+",id="+ci.getId()));
            }
            if(hornetEnabled) {
                hornetChannelMgr.clientInfoChanged(ci);
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a channel", e);
        }

    }

    public void createChannel(ChannelRequest cr, Privilege priv) throws YamcsException{
        log.info("Creating a new channel instance="+cr.getInstance()+" name="+cr.getName()+" type="+cr.getType()+" spec="+cr.getSpec()+"' persistent="+cr.getPersistent());
        String currentUser=priv.getCurrentUser();
        if(currentUser==null) currentUser="unknown";

        if(!priv.hasPrivilege(Privilege.Type.SYSTEM, "MayControlChannel")) {
            if(cr.getPersistent()) { 
                log.warn("User "+currentUser+" is not allowed to create persistent channels");
                throw new YamcsException("permission denied");
            }
            if(!"Archive".equals(cr.getType())) {
                //without MayControlChannel privilege, only hrdp archive and pacts files are allowed
                log.warn("User "+currentUser+" is not allowed to create channels of type "+cr.getType());
                throw new YamcsException("permission denied");
            }
            for(int i=0;i<cr.getClientIdCount();i++) {
                ClientInfo si=clients.get(cr.getClientId(i)).getClientInfo();
                if(!currentUser.equals(si.getUsername())) {
                    log.warn("User "+currentUser+" is not allowed to connect "+si.getUsername()+" to a new channel "+cr.getName() );
                    throw new YamcsException("permission denied");
                }
            }
        }


        try {
            int n=0;
            Channel chan;
            chan = ChannelFactory.create(cr.getInstance(), cr.getName(), cr.getType(), cr.getSpec(),"unknown");
            chan.setPersistent(cr.getPersistent());
            for(int i=0;i<cr.getClientIdCount();i++) {
                ClientControlImpl cci=clients.get(cr.getClientId(i));
                if(cci!=null) {
                    switchChannel(cci, chan);
                    n++;
                } else {
                    log.warn("createChannel called with invalid client id:"+cr.getClientId(i)+"; ignored.");
                }
            }
            if(n>0 || cr.getPersistent()) {
                log.info("starting new channel'" + chan.getName() + "' with " + chan.getConnectedClients() + " clients");
                chan.start();
            } else {
                chan.quit();
                throw new YamcsException("createChannel invoked with a list full of invalid client ids");
            }
        } catch (ChannelException e) {
            throw new YamcsException(e.getMessage(), e.getCause());
        } catch (ConfigurationException e) {
            e.printStackTrace();
            throw new YamcsException(e.getMessage(), e.getCause());
        }
    }


    public void connectToChannel(ChannelRequest cr, Privilege priv) throws YamcsException {
        Channel chan=Channel.getInstance(cr.getInstance(), cr.getName());
        if(chan==null) throw new YamcsException("Unexisting channel ("+cr.getInstance()+", "+cr.getName()+") specified");


        String currentUser=priv.getCurrentUser();
        if(currentUser==null) currentUser="unknown";

        log.debug("User "+ currentUser+" wants to connect clients "+cr.getClientIdList()+" to channel "+cr.getName());
        
        
        if(!priv.hasPrivilege(Privilege.Type.SYSTEM, "MayControlChannel") &&
                !((chan.isPersistent() || chan.getCreator().equals(currentUser)))) {
            log.warn("User "+currentUser+" is not allowed to connect users to channel "+cr.getName() );
            throw new YamcsException("permission denied");
        }
        if(!priv.hasPrivilege(Privilege.Type.SYSTEM, "MayControlChannel")) {
            for(int i=0; i<cr.getClientIdCount(); i++) {
                ClientInfo si=clients.get(cr.getClientId(i)).getClientInfo();
                if(!currentUser.equals(si.getUsername())) {
                    log.warn("User "+currentUser+" is not allowed to connect "+si.getUsername()+" to channel "+cr.getName());
                    throw new YamcsException("permission denied");
                }
            }
        }

        try {
            for(int i=0;i<cr.getClientIdCount();i++) {
                int id=cr.getClientId(i);
                ClientControlImpl cci=clients.get(id);
                switchChannel(cci, chan);
            }
        } catch(ChannelException e) {
            throw new YamcsException(e.toString());
        }
    }

    public void registerCommandQueueManager(String instance, String channelName, CommandQueueManager cqm) {
        try {
            for(CommandQueue cq:cqm.getQueues()) {
                if(jmxEnabled) {
                    CommandQueueControlImpl cqci = new CommandQueueControlImpl(instance, channelName, cqm, cq);
                    mbeanServer.registerMBean(cqci, ObjectName.getInstance(tld+"."+instance+":type=commandQueues,channel="+channelName+",name="+cq.getName()));
                }
            }
            if(hornetEnabled) {
                hornetCmdQueueMgr.registerCommandQueueManager(cqm);
            }
        } catch (Exception e) {
            log.warn("Got exception when registering a command queue", e);
        }
    }
}
