package org.yamcs.management;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.ProcessorListener;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueListener;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.management.StreamControlImpl;
import org.yamcs.yarch.management.TableControlImpl;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;

public class JMXService extends AbstractService
        implements ManagementListener, LinkListener, ProcessorListener, TableStreamListener, CommandQueueListener {
    final MBeanServer mbeanServer;
    static Logger log = LoggerFactory.getLogger(JMXService.class.getName());
    private static final String TOP_LEVEL_NAME = "yamcs";

    // keep track of registered services
    Map<String, Integer> servicesCount = new ConcurrentHashMap<>();

    Map<ObjectName, LinkControlImpl> links = new HashMap<>();

    public JMXService() {
        mbeanServer = ManagementFactory.getPlatformMBeanServer();
        ManagementService mgmSrv = ManagementService.getInstance();
        mgmSrv.addCommandQueueListener(this);
        mgmSrv.addLinkListener(this);
        mgmSrv.addManagementListener(this);
        mgmSrv.addTableStreamListener(this);
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    @Override
    public void serviceRegistered(String instance, String serviceName, Service service) {
        ServiceControlImpl sci;
        try {
            sci = new ServiceControlImpl(service);

            // if a service with the same name has already been registered, suffix the service name with an index
            int serviceCount = 0;
            if (servicesCount.containsKey(serviceName)) {
                serviceCount = servicesCount.get(serviceName);
                servicesCount.remove(serviceName);
            }
            servicesCount.put(serviceName, ++serviceCount);
            if (serviceCount > 1) {
                serviceName = serviceName + "_" + serviceCount;
            }

            // register service
            mbeanServer.registerMBean(sci,
                    ObjectName.getInstance(TOP_LEVEL_NAME + "." + instance + ":type=services,name=" + serviceName));

        } catch (Exception e) {
            log.warn("Got exception when registering a service", e);
        }

    }

    @Override
    public void serviceUnregistered(String instance, String serviceName) {
        try {
            // check if this serviceName has been registered several time
            int serviceCount = 0;
            String serviceName_ = serviceName;
            if (servicesCount.containsKey(serviceName) && (serviceCount = servicesCount.get(serviceName)) > 0) {
                if (serviceCount > 1) {
                    serviceName_ = serviceName + "_" + serviceCount;
                }
                serviceCount--;
                servicesCount.replace(serviceName, serviceCount);
            }

            // unregister service
            mbeanServer.unregisterMBean(
                    ObjectName.getInstance(TOP_LEVEL_NAME + "." + instance + ":type=services,name=" + serviceName_));
        } catch (Exception e) {
            log.warn("Got exception when registering a service", e);
        }
    }

    @Override
    public void streamRegistered(String instance, Stream stream) {
        StreamControlImpl sci;
        try {
            sci = new StreamControlImpl(stream);
            mbeanServer.registerMBean(sci,
                    ObjectName.getInstance(TOP_LEVEL_NAME + "." + instance + ":type=streams,name=" + stream.getName()));
        } catch (Exception e) {
            log.warn("Got exception when registering a stream: ", e);
        }
    }

    @Override
    public void streamUnregistered(String instance, String streamName) {
        try {
            mbeanServer.unregisterMBean(
                    ObjectName.getInstance(TOP_LEVEL_NAME + "." + instance + ":type=streams,name=" + streamName));
        } catch (Exception e) {
            log.warn("Got exception when unregistering a stream: ", e);
        }
    }

    @Override
    public void tableRegistered(String instance, TableDefinition table) {
        try {
            TableControlImpl tci = new TableControlImpl(table);
            mbeanServer.registerMBean(tci,
                    ObjectName.getInstance(TOP_LEVEL_NAME + "." + instance + ":type=tables,name=" + table.getName()));
        } catch (Exception e) {
            log.warn("Got exception when registering a stream: ", e);
        }
    }

    @Override
    public void tableUnregistered(String instance, String tableName) {
        try {
            mbeanServer.unregisterMBean(
                    ObjectName.getInstance(TOP_LEVEL_NAME + "." + instance + ":type=tables,name=" + tableName));
        } catch (Exception e) {
            log.warn("Got exception when unregistering a stream: ", e);
        }
    }

    @Override
    public void linkRegistered(LinkInfo linkInfo) {
        try {
            LinkControlImpl lci = new LinkControlImpl(linkInfo);
            ObjectName on = getLinkObjectName(linkInfo);
            mbeanServer.registerMBean(lci, on);
            links.put(on, lci);
        } catch (Exception e) {
            log.warn("Got exception when registering a link", e);
        }
    }

    @Override
    public void linkUnregistered(LinkInfo linkInfo) {
        try {
            ObjectName on = getLinkObjectName(linkInfo);
            mbeanServer.unregisterMBean(on);
            links.remove(on);
        } catch (Exception e) {
            log.warn("Got exception when unregistering a link", e);
        }
    }

    ObjectName getLinkObjectName(LinkInfo linkInfo) throws MalformedObjectNameException, NullPointerException {
        return ObjectName
                .getInstance(TOP_LEVEL_NAME + "." + linkInfo.getInstance() + ":type=links,name=" + linkInfo.getName());
    }

    @Override
    public void linkChanged(LinkInfo linkInfo) {
        try {
            LinkControlImpl lci = links.get(getLinkObjectName(linkInfo));
            if (lci != null) {
                lci.linkInfo = linkInfo;
            }
        } catch (Exception e) {
            log.warn("Got exception when changing a link", e);
        }
    }

    @Override
    public void processorAdded(Processor processor) {
        try {
            ProcessorControlImpl cci = new ProcessorControlImpl(processor);
            mbeanServer.registerMBean(cci, getProcessorObjectName(processor));
        } catch (Exception e) {
            log.warn("Got exception when registering a processor", e);
        }

    }

    @Override
    public void processorClosed(Processor processor) {
        try {
            mbeanServer.unregisterMBean(getProcessorObjectName(processor));
        } catch (Exception e) {
            log.warn("Got exception when unregistering a processor", e);
        }
    }

    @Override
    public void processorStateChanged(Processor processor) {// don't care
    }

    ObjectName getProcessorObjectName(Processor proc) throws MalformedObjectNameException {
        return ObjectName
                .getInstance(TOP_LEVEL_NAME + "." + proc.getInstance() + ":type=processors,name=" + proc.getName());
    }

    @Override
    public void commandQueueRegistered(String instance, String processorName, CommandQueue q) {
        try {
            CommandQueueControlImpl cqci = new CommandQueueControlImpl(instance, processorName, q);
            mbeanServer.registerMBean(cqci, getCommandQueueObjectName(instance, processorName, q));
        } catch (Exception e) {
            log.warn("Got exception when registering a command queue", e);
        }
    }

    @Override
    public void commandQueueUnregistered(String instance, String processorName, CommandQueue q) {
        try {
            CommandQueueControlImpl cqci = new CommandQueueControlImpl(instance, processorName, q);
            mbeanServer.registerMBean(cqci, getCommandQueueObjectName(instance, processorName, q));
        } catch (Exception e) {
            log.warn("Got exception when registering a command queue", e);
        }
    }

    ObjectName getCommandQueueObjectName(String instance, String processorName, CommandQueue cq)
            throws MalformedObjectNameException {
        return ObjectName.getInstance(TOP_LEVEL_NAME + "." + instance + ":type=commandQueues,processor=" + processorName
                + ",name=" + cq.getName());
    }

    @Override
    public void clientRegistered(ConnectedClient client) {
        try {
            ClientControlImpl cci = new ClientControlImpl(client);
            mbeanServer.registerMBean(cci, getClientInfoObjectName(client));
        } catch (Exception e) {
            log.warn("Got exception when registering a client", e);
        }
    }

    @Override
    public void clientUnregistered(ConnectedClient client) {
        try {
            mbeanServer.unregisterMBean(getClientInfoObjectName(client));
        } catch (Exception e) {
            log.warn("Got exception when unregistering a client", e);
        }
    }

    @Override
    public void clientInfoChanged(ConnectedClient client) {
        try {
            // TODO mbeanServer.unregisterMBean(getClientInfoObjectName(oldci));
            ClientControlImpl cci = new ClientControlImpl(client);
            mbeanServer.registerMBean(cci, getClientInfoObjectName(client));
        } catch (Exception e) {
            log.warn("Got exception when changing a client registration", e);
        }
    }

    ObjectName getClientInfoObjectName(ConnectedClient client) throws MalformedObjectNameException {
        int clientId = client.getId();
        String instance = client.getProcessor().getInstance();
        String processor = client.getProcessor().getName();
        return ObjectName.getInstance(
                TOP_LEVEL_NAME + "." + instance + ":type=clients,processor=" + processor + ",id=" + clientId);
    }
}
