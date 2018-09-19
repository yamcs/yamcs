package org.yamcs.management;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ConnectedClient;
import org.yamcs.InstanceStateListener;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.ProcessorFactory;
import org.yamcs.ProcessorListener;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.commanding.CommandQueue;
import org.yamcs.commanding.CommandQueueListener;
import org.yamcs.commanding.CommandQueueManager;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.Statistics;
import org.yamcs.tctm.Link;
import org.yamcs.xtceproc.ProcessingStatistics;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;

import com.google.common.util.concurrent.Service;

/**
 * Responsible for providing to interested listeners info related to creation/removal/update of:
 * <ul>
 * <li>instances, processors and clients - see {@link ManagementListener}
 * <li>links - see {@link LinkListener}
 * <li>streams and tables - see {@link TableStreamListener}
 * <li>command queues - see {@link CommandQueueListener}
 * </ul>
 */
public class ManagementService implements ProcessorListener {
    static Logger log = LoggerFactory.getLogger(ManagementService.class.getName());
    static ManagementService managementService = new ManagementService();

    Map<Integer, ConnectedClient> clients = Collections.synchronizedMap(new HashMap<Integer, ConnectedClient>());
    private AtomicInteger clientIdGenerator = new AtomicInteger();

    List<LinkWithInfo> links = new CopyOnWriteArrayList<>();
    List<CommandQueueManager> qmanagers = new CopyOnWriteArrayList<>();

    // Used to update TM-statistics, and Link State
    ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    // Processors & Clients. Should maybe split up
    Set<ManagementListener> managementListeners = new CopyOnWriteArraySet<>();

    Set<LinkListener> linkListeners = new CopyOnWriteArraySet<>();
    Set<CommandQueueListener> commandQueueListeners = new CopyOnWriteArraySet<>();
    Set<TableStreamListener> tableStreamListeners = new CopyOnWriteArraySet<>();

    Map<Processor, Statistics> processors = new ConcurrentHashMap<>();

    // we use this one because ConcurrentHashMap does not support null values
    static final Statistics STATS_NULL = Statistics.newBuilder().setInstance("null").setYProcessorName("null").build();

    static public ManagementService getInstance() {
        return managementService;
    }

    private InstanceStateListener instanceListener;

    private ManagementService() {
        Processor.addProcessorListener(this);
        timer.scheduleAtFixedRate(() -> updateStatistics(), 1, 1, TimeUnit.SECONDS);
        timer.scheduleAtFixedRate(() -> checkLinkUpdate(), 1, 1, TimeUnit.SECONDS);
    }

    public void shutdown() {
        managementListeners.clear();
    }

    public void registerService(String instance, String serviceName, Service service) {
        managementListeners.forEach(l -> l.serviceRegistered(instance, serviceName, service));
    }

    public void unregisterService(String instance, String serviceName) {
        managementListeners.forEach(l -> l.serviceUnregistered(instance, serviceName));
    }

    public void registerLink(String instance, String linkName, String spec, Link link) {
        LinkInfo.Builder linkb = LinkInfo.newBuilder().setInstance(instance)
                .setName(linkName)
                .setDisabled(link.isDisabled())
                .setStatus(link.getLinkStatus().name())
                .setType(link.getClass().getName())
                .setSpec(spec)
                .setDataInCount(link.getDataInCount())
                .setDataOutCount(link.getDataOutCount())
                .setDataCount(link.getDataInCount() + link.getDataOutCount());
        if (link.getDetailedStatus() != null) {
            linkb.setDetailedStatus(link.getDetailedStatus());
        }
        LinkInfo linkInfo = linkb.build();
        links.add(new LinkWithInfo(link, linkInfo));
        linkListeners.forEach(l -> l.linkRegistered(linkInfo));
    }

    public void unregisterLink(String instance, String linkName) {
        Optional<LinkWithInfo> o = getLink(instance, linkName);
        if (o.isPresent()) {
            LinkWithInfo lwi = o.get();
            links.remove(lwi);
            linkListeners.forEach(l -> l.linkUnregistered(lwi.linkInfo));
        }
    }

    private Optional<LinkWithInfo> getLink(String instance, String linkName) {
        return links.stream()
                .filter(lwi -> instance.equals(lwi.linkInfo.getInstance()) && linkName.equals(lwi.linkInfo.getName()))
                .findFirst();
    }

    public CommandQueueManager getQueueManager(String instance, String processorName) throws YamcsException {
        for (int i = 0; i < qmanagers.size(); i++) {
            CommandQueueManager cqm = qmanagers.get(i);
            if (cqm.getInstance().equals(instance) && cqm.getChannelName().equals(processorName)) {
                return cqm;
            }
        }

        throw new YamcsException("Cannot find a command queue manager for " + instance + "/" + processorName);
    }

    public List<CommandQueueManager> getQueueManagers() {
        return qmanagers;
    }

    public void registerClient(ConnectedClient client) {
        int id = clientIdGenerator.incrementAndGet();
        client.setClientId(id);
        try {
            clients.put(id, client);
            managementListeners.forEach(l -> l.clientRegistered(client));
        } catch (Exception e) {
            log.warn("Got exception when registering a client", e);
        }
    }

    public void unregisterClient(int id) {
        ConnectedClient client = clients.remove(id);
        if (client == null) {
            return;
        }
        Processor processor = client.getProcessor();
        if (processor != null) {
            processor.disconnect(client);
        }
        try {
            managementListeners.forEach(l -> l.clientUnregistered(client));
        } catch (Exception e) {
            log.warn("Got exception when unregistering a client", e);
        }
    }

    private void switchProcessor(ConnectedClient client, Processor newProcessor) throws ProcessorException {
        Processor oldProcessor = client.getProcessor();
        if (oldProcessor != null) {
            oldProcessor.disconnect(client);
        }
        client.setProcessor(newProcessor);
        newProcessor.connect(client);
        try {
            managementListeners.forEach(l -> l.clientInfoChanged(client));
        } catch (Exception e) {
            log.warn("Got exception when switching processor", e);
        }
    }

    public void createProcessor(ProcessorManagementRequest pmr, String username) throws YamcsException {
        log.info("Creating new processor instance: {}, name: {}, type: {}, config: {}, persistent: {}",
                pmr.getInstance(), pmr.getName(), pmr.getType(), pmr.getConfig(), pmr.getPersistent());
        Processor processor;
        try {
            int n = 0;

            Object spec = null;
            if (pmr.hasConfig()) {
                spec = pmr.getConfig();
            }
            processor = ProcessorFactory.create(pmr.getInstance(), pmr.getName(), pmr.getType(), username, spec);
            processor.setPersistent(pmr.getPersistent());
            for (int i = 0; i < pmr.getClientIdCount(); i++) {
                ConnectedClient client = clients.get(pmr.getClientId(i));
                if (client != null) {
                    switchProcessor(client, processor);
                    n++;
                } else {
                    log.warn("createProcessor called with invalid client id: {}; ignored.", pmr.getClientId(i));
                }
            }
            if (n > 0 || pmr.getPersistent()) {
                log.info("Starting new processor '{}' with {} clients", processor.getName(),
                        processor.getConnectedClients());
                processor.startAsync();
                processor.awaitRunning();
            } else {
                processor.quit();
                throw new YamcsException("createProcessor invoked with a list full of invalid client ids");
            }
        } catch (ProcessorException | ConfigurationException e) {
            throw new YamcsException(e.getMessage(), e.getCause());
        } catch (IllegalStateException e1) {
            Throwable t = e1.getCause();
            if (t instanceof YamcsException) {
                throw (YamcsException) t;
            } else {
                throw new YamcsException(t.getMessage(), t.getCause());
            }
        }
    }

    public void connectToProcessor(Processor processor, int clientId) throws YamcsException, ProcessorException {
        ConnectedClient client = clients.get(clientId);
        if (client == null) {
            throw new YamcsException("Invalid client id " + clientId);
        }
        switchProcessor(client, processor);
    }

    public void connectToProcessor(ProcessorManagementRequest cr) throws YamcsException {
        Processor processor = Processor.getInstance(cr.getInstance(), cr.getName());
        if (processor == null) {
            throw new YamcsException("Unexisting processor " + cr.getInstance() + "/" + cr.getName() + " specified");
        }

        log.debug("Connecting clients {} to processor {}", cr.getClientIdList(), cr.getName());

        try {
            for (int i = 0; i < cr.getClientIdCount(); i++) {
                int id = cr.getClientId(i);
                ConnectedClient client = clients.get(id);
                switchProcessor(client, processor);
            }
        } catch (ProcessorException e) {
            throw new YamcsException(e.toString());
        }
    }

    public void registerCommandQueueManager(String instance, String processorName, CommandQueueManager cqm) {
        for (CommandQueue cq : cqm.getQueues()) {
            commandQueueListeners.forEach(l -> l.commandQueueRegistered(instance, processorName, cq));
        }
        qmanagers.add(cqm);
        for (CommandQueueListener l : commandQueueListeners) {
            cqm.registerListener(l);
            for (CommandQueue q : cqm.getQueues()) {
                l.updateQueue(q);
            }
        }

    }

    public void unregisterCommandQueueManager(String instance, String processorName, CommandQueueManager cqm) {
        try {
            for (CommandQueue cq : cqm.getQueues()) {
                commandQueueListeners.forEach(l -> l.commandQueueUnregistered(instance, processorName, cq));
            }
            qmanagers.remove(cqm);
        } catch (Exception e) {
            log.warn("Got exception when unregistering a command queue", e);
        }
    }

    public List<CommandQueueManager> getCommandQueueManagers() {
        return qmanagers;
    }

    public CommandQueueManager getCommandQueueManager(Processor processor) {
        for (CommandQueueManager mgr : qmanagers) {
            if (mgr.getInstance().equals(processor.getInstance())
                    && mgr.getChannelName().equals(processor.getName())) {
                return mgr;
            }
        }
        return null;
    }

    public void enableLink(String instance, String linkName) {
        log.debug("received enableLink for {}/{}", instance, linkName);
        Optional<LinkWithInfo> o = getLink(instance, linkName);
        if (o.isPresent()) {
            LinkWithInfo lci = o.get();
            lci.link.enable();
        } else {
            throw new IllegalArgumentException("There is no link named '" + linkName + "' in instance " + instance);
        }
    }

    public void disableLink(String instance, String linkName) {
        log.debug("received disableLink for {}/{}", instance, linkName);
        Optional<LinkWithInfo> o = getLink(instance, linkName);
        if (o.isPresent()) {
            LinkWithInfo lci = o.get();
            lci.link.disable();
        } else {
            throw new IllegalArgumentException("There is no link named '" + linkName + "' in instance " + instance);
        }
    }

    /**
     * Adds a listener that is to be notified when any processor, or any client is updated. Calling this multiple times
     * has no extra effects. Either you listen, or you don't.
     */
    public boolean addManagementListener(ManagementListener l) {
        return managementListeners.add(l);
    }

    /**
     * Adds a listener that is to be notified when any processor, or any client is updated. Calling this multiple times
     * has no extra effects. Either you listen, or you don't.
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

    public boolean addTableStreamListener(TableStreamListener l) {
        return tableStreamListeners.add(l);
    }

    public boolean removeTableStreamListener(TableStreamListener l) {
        return tableStreamListeners.remove(l);
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
        return links.stream().map(lwi -> lwi.linkInfo).collect(Collectors.toList());
    }

    public List<LinkInfo> getLinkInfo(String instance) {
        return links.stream()
                .map(lwi -> lwi.linkInfo)
                .filter(li -> li.getInstance().equals(instance))
                .collect(Collectors.toList());
    }

    public LinkInfo getLinkInfo(String instance, String name) {
        Optional<LinkInfo> o = links.stream()
                .map(lwi -> lwi.linkInfo)
                .filter(li -> li.getInstance().equals(instance) && li.getName().equals(name))
                .findFirst();
        if (o.isPresent()) {
            return o.get();
        } else {
            return null;
        }
    }

    public Set<ConnectedClient> getClients() {
        synchronized (clients) {
            return new HashSet<>(clients.values());
        }
    }

    public Set<ConnectedClient> getClients(String username) {
        synchronized (clients) {
            return clients.values().stream()
                    .filter(client -> client.getUser().getUsername().equals(username))
                    .collect(Collectors.toSet());
        }
    }

    public ConnectedClient getClient(int clientId) {
        return clients.get(clientId);
    }

    private void updateStatistics() {
        try {
            for (Entry<Processor, Statistics> entry : processors.entrySet()) {
                Processor yproc = entry.getKey();
                Statistics stats = entry.getValue();
                ProcessingStatistics ps = yproc.getTmProcessor().getStatistics();
                if ((stats == STATS_NULL) || (ps.getLastUpdated() > stats.getLastUpdated())) {
                    stats = ManagementGpbHelper.buildStats(yproc);
                    processors.put(yproc, stats);
                }
                if (stats != STATS_NULL) {
                    for (ManagementListener l : managementListeners) {
                        l.statisticsUpdated(yproc, stats);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error updating statistics ", e);
        }
    }

    private void checkLinkUpdate() {
        // see if any link has changed
        for (LinkWithInfo lwi : links) {
            if (lwi.hasChanged()) {
                LinkInfo li = lwi.linkInfo;
                linkListeners.forEach(l -> l.linkChanged(li));
            }
        }
    }

    @Override
    public void processorAdded(Processor processor) {
        ProcessorInfo pi = ManagementGpbHelper.toProcessorInfo(processor);
        managementListeners.forEach(l -> l.processorAdded(pi));
        processors.put(processor, STATS_NULL);
    }

    @Override
    public void processorClosed(Processor processor) {
        ProcessorInfo pi = ManagementGpbHelper.toProcessorInfo(processor);
        managementListeners.forEach(l -> l.processorClosed(pi));
        processors.remove(processor);
    }

    @Override
    public void processorStateChanged(Processor processor) {
        ProcessorInfo pi = ManagementGpbHelper.toProcessorInfo(processor);
        managementListeners.forEach(l -> l.processorStateChanged(pi));
    }

    public void registerYamcsInstance(YamcsServerInstance ys) {
        instanceListener = new InstanceStateListener() {
            @Override
            public void initializing() {
                managementListeners.forEach(l -> l.instanceStateChanged(ys));
            }

            @Override
            public void initialized() {
                managementListeners.forEach(l -> l.instanceStateChanged(ys));
            }

            @Override
            public void starting() {
                managementListeners.forEach(l -> l.instanceStateChanged(ys));
            }

            @Override
            public void running() {
                managementListeners.forEach(l -> l.instanceStateChanged(ys));
            }

            @Override
            public void stopping() {
                managementListeners.forEach(l -> l.instanceStateChanged(ys));
            }

            @Override
            public void offline() {
                managementListeners.forEach(l -> l.instanceStateChanged(ys));
            }

            @Override
            public void failed(Throwable failure) {
                managementListeners.forEach(l -> l.instanceStateChanged(ys));
            }
        };
        ys.addStateListener(instanceListener);
    }

    /**
     * Restarts a yamcs instance. It is not possible to restart an instance - so the old one is stopped and a new one is
     * created and started.
     * 
     * @param instanceName
     *            the name of the instance to be restarted
     * @return completable that will complete after the instance has been restarted
     */
    public CompletableFuture<Void> restartInstance(String instanceName) {
        YamcsServerInstance ysi = YamcsServer.getInstance(instanceName);
        if (ysi == null) {
            throw new IllegalArgumentException("No instance named '" + instanceName + "'");
        }

        return CompletableFuture.runAsync(() -> {
            log.info("Restarting the instance {}", instanceName);
            YamcsServer.restartYamcsInstance(instanceName);
        });

    }

    public CompletableFuture<Void> stopInstance(String instanceName) {
        YamcsServerInstance ys = YamcsServer.getInstance(instanceName);
        if (ys == null) {
            throw new IllegalArgumentException("No instance named '" + instanceName + "'");
        }
        return CompletableFuture.runAsync(() -> ys.stop());
    }

    public void registerTable(String instance, TableDefinition tblDef) {
        tableStreamListeners.forEach(l -> l.tableRegistered(instance, tblDef));
    }

    public void registerStream(String instance, Stream stream) {
        tableStreamListeners.forEach(l -> l.streamRegistered(instance, stream));
    }

    public void unregisterTable(String instance, String tblName) {
        tableStreamListeners.forEach(l -> l.tableUnregistered(instance, tblName));
    }

    public void unregisterStream(String instance, String name) {
        tableStreamListeners.forEach(l -> l.tableUnregistered(instance, name));
    }

    static class LinkWithInfo {
        final Link link;
        LinkInfo linkInfo;

        public LinkWithInfo(Link link, LinkInfo linkInfo) {
            this.link = link;
            this.linkInfo = linkInfo;
        }

        boolean hasChanged() {
            if (!linkInfo.getStatus().equals(link.getLinkStatus().name())
                    || linkInfo.getDisabled() != link.isDisabled()
                    || linkInfo.getDataInCount() != link.getDataInCount()
                    || linkInfo.getDataOutCount() != link.getDataOutCount()
                    || !linkInfo.getDetailedStatus().equals(link.getDetailedStatus())) {

                linkInfo = LinkInfo.newBuilder(linkInfo)
                        .setDisabled(link.isDisabled())
                        .setStatus(link.getLinkStatus().name())
                        .setDetailedStatus(link.getDetailedStatus())
                        .setDataInCount(link.getDataInCount())
                        .setDataOutCount(link.getDataOutCount())
                        .setDataCount(link.getDataInCount() + link.getDataOutCount()).build();
                return true;
            } else {
                return false;
            }
        }

    }
}
