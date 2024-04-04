package org.yamcs.management;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
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
import org.yamcs.mdb.ProcessingStatistics;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.ProcessorManagementRequest;
import org.yamcs.protobuf.Statistics;
import org.yamcs.protobuf.Table.StreamInfo;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;

import com.google.common.util.concurrent.Service;

/**
 * Responsible for providing to interested listeners info related to creation/removal/update of:
 * <ul>
 * <li>instances and processors - see {@link ManagementListener}
 * <li>streams and tables - see {@link TableStreamListener}
 * <li>command queues - see {@link CommandQueueListener}
 * </ul>
 */
public class ManagementService implements ProcessorListener {
    static Logger log = LoggerFactory.getLogger(ManagementService.class.getName());
    static ManagementService managementService = new ManagementService();

    // streams is accessed only from the timer thread
    List<StreamWithInfo> streams = new ArrayList<>();

    List<CommandQueueManager> qmanagers = new CopyOnWriteArrayList<>();
    List<Processor> processors = new CopyOnWriteArrayList<>();

    // Processors & Clients. Should maybe split up
    Set<ManagementListener> managementListeners = new CopyOnWriteArraySet<>();

    Set<CommandQueueListener> commandQueueListeners = new CopyOnWriteArraySet<>();
    Set<TableStreamListener> tableStreamListeners = new CopyOnWriteArraySet<>();

    static public ManagementService getInstance() {
        return managementService;
    }

    private InstanceStateListener instanceListener;

    public void init() {
        var timer = YamcsServer.getServer().getThreadPoolExecutor();
        Processor.addProcessorListener(this);
        timer.scheduleAtFixedRate(() -> updateStatistics(), 1, 1, TimeUnit.SECONDS);
        timer.scheduleAtFixedRate(() -> checkStreamUpdate(), 1, 1, TimeUnit.SECONDS);
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
            if (n > 0 || pmr.getPersistent()) {
                log.info("Starting new processor '{}'", processor.getName());
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

    /**
     * Adds a listener that is to be notified when any processor, or any client is updated. Calling this multiple times
     * has no extra effects. Either you listen, or you don't.
     */
    public boolean addManagementListener(ManagementListener l) {
        return managementListeners.add(l);
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

    private void updateStatistics() {
        for (Processor processor : processors) {
            ProcessingStatistics ps = processor.getTmProcessor().getStatistics();
            Statistics stats = ManagementGpbHelper.buildStats(processor, ps.snapshot());
            for (ManagementListener l : managementListeners) {
                l.statisticsUpdated(processor, stats);
            }
        }
    }

    private void checkStreamUpdate() {
        for (StreamWithInfo stream : streams) {
            if (stream.hasChanged()) {
                tableStreamListeners.forEach(l -> l.streamUpdated(
                        stream.instance, stream.streamInfo));
            }
        }
    }

    @Override
    public void processorAdded(Processor processor) {
        ProcessorInfo pi = ManagementGpbHelper.toProcessorInfo(processor);
        managementListeners.forEach(l -> l.processorAdded(pi));
        processors.add(processor);
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

    public void registerTable(String instance, TableDefinition tblDef) {
        tableStreamListeners.forEach(l -> l.tableRegistered(instance, tblDef));
    }

    public void registerStream(String instance, Stream stream) {
        var timer = YamcsServer.getServer().getThreadPoolExecutor();

        timer.execute(() -> {
            StreamInfo.Builder streamb = StreamInfo.newBuilder()
                    .setName(stream.getName())
                    .setDataCount(stream.getDataCount());
            StreamInfo streamInfo = streamb.build();
            streams.add(new StreamWithInfo(instance, stream, streamInfo));
        });

        tableStreamListeners.forEach(l -> l.streamRegistered(instance, stream));
    }

    public void unregisterTable(String instance, String tblName) {
        tableStreamListeners.forEach(l -> l.tableUnregistered(instance, tblName));
    }

    public void unregisterStream(String instance, String name) {
        var timer = YamcsServer.getServer().getThreadPoolExecutor();

        tableStreamListeners.forEach(l -> l.streamUnregistered(instance, name));
        timer.execute(() -> {
            streams.removeIf(swi -> swi.instance.equals(instance) && swi.stream.getName().equals(name));
        });
    }

    static class StreamWithInfo {
        final String instance;
        final Stream stream;
        StreamInfo streamInfo;

        public StreamWithInfo(String instance, Stream stream, StreamInfo streamInfo) {
            this.instance = instance;
            this.stream = stream;
            this.streamInfo = streamInfo;
        }

        boolean hasChanged() {
            if (streamInfo.getDataCount() != stream.getDataCount()) {
                streamInfo = StreamInfo.newBuilder(streamInfo)
                        .setDataCount(stream.getDataCount())
                        .build();

                return true;
            } else {
                return false;
            }
        }
    }
}
