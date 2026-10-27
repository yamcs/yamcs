package org.yamcs.archive;

import static org.yamcs.StandardTupleDefinitions.TM_ROOT_CONTAINER_COLUMN;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.InitException;
import org.yamcs.ProcessorConfig;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.TmStreamConfigEntry;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.mdb.ContainerProcessingResult;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.mdb.XtceTmExtractor;
import org.yamcs.time.TimeService;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TimePartitionSchema;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Records XTCE TM sequence containers.
 * <p>
 * The main job of this class is to populate the "pname" column of the tm table. The other columns are copied verbatim
 * from the TM input streams. It does that by creating a {@link XtceTmExtractor} and subscribing to all sequence
 * containers having the flag {@link SequenceContainer#useAsPacketName()} set. The pname is the qualified name of
 * the most specific (lowest in the XTCE hierarchy) container matching the telemetry packet.
 * 
 * <p>
 * It subscribes to all the streams configured with the "streams" config key or, if not present, to all TM streams
 * defined in the instance (streamConfig section of the instance configuration).
 * 
 */
public class XtceTmRecorder extends AbstractYamcsService {
    public static final String REC_STREAM_NAME = "xtce_tm_recorder_stream";
    public static final String TABLE_NAME = "tm";
    public static final String PNAME_COLUMN = "pname";
    public static final String CF_NAME = "rt_data";

    public static final TupleDefinition RECORDED_TM_TUPLE_DEFINITION;
    static {
        RECORDED_TM_TUPLE_DEFINITION = StandardTupleDefinitions.TM.copy();
        RECORDED_TM_TUPLE_DEFINITION.removeColumn(TM_ROOT_CONTAINER_COLUMN);
        RECORDED_TM_TUPLE_DEFINITION.addColumn(PNAME_COLUMN, DataType.ENUM); // container name (XTCE qualified name)
    }

    private long totalNumPackets;

    final Tuple END_MARK = new Tuple(StandardTupleDefinitions.TM,
            new Object[] { null, null, null, null, null, null, null, null, null });

    Mdb mdb;
    TmPartitionGroups partitionGroups;

    private final List<StreamRecorder> recorders = new ArrayList<>();

    TimeService timeService;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("streams", OptionType.LIST).withElementType(OptionType.STRING);
        spec.addOption("partitionByPname", OptionType.BOOLEAN).withDefault(false);
        Spec groupsSpec = new Spec();
        groupsSpec.allowUnknownKeys(true);
        spec.addOption("partitionGroups", OptionType.MAP).withSpec(groupsSpec);
        spec.mutuallyExclusive("partitionByPname", "partitionGroups");
        spec.addOption("receptionTimeIndex", OptionType.BOOLEAN).withDefault(false);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        var timePart = ydb.getTimePartitioningSchema(config);

        mdb = MdbFactory.getInstance(yamcsInstance);

        // these settings are only used when creating the table; an existing table keeps its definition
        boolean partitionByPname = config.getBoolean("partitionByPname");
        Map<String, List<String>> groupDefinitions = config.containsKey("partitionGroups")
                ? TmPartitionGroups.parseConfig(config.getConfig("partitionGroups"))
                : null;
        try {
            TableDefinition tblDef = ydb.getTable(TABLE_NAME);
            if (tblDef == null) {
                String query = "create table " + TABLE_NAME + "("
                        + getRecordedTupleDefinition(groupDefinitions != null).getStringDefinition1()
                        + ", " + getPrimaryKey(partitionByPname) + ") histogram(pname) "
                        + (config.getBoolean("receptionTimeIndex", false) ? ", index(rectime, pname)" : "")
                        + getPartitionBy(timePart, partitionByPname, groupDefinitions != null)
                        + " table_format=compressed,column_family:" + CF_NAME;
                ydb.execute(query);
                tblDef = ydb.getTable(TABLE_NAME);
            }
            if (TmPartitionGroups.isPartitionedByGroup(tblDef)) {
                // if the option has been removed from the configuration, everything goes into the default group
                partitionGroups = new TmPartitionGroups(yamcsInstance, mdb,
                        groupDefinitions == null ? Map.of() : groupDefinitions);
            } else if (groupDefinitions != null) {
                log.warn("The partitionGroups option is ignored because the existing {} table is not partitioned "
                        + "by group. The option is only used when creating the table.", TABLE_NAME);
            }

            TmReceptionTimeIndex.start(ydb, config.getBoolean("receptionTimeIndex", false));

            ydb.execute("create stream " + REC_STREAM_NAME
                    + getRecordedTupleDefinition(partitionGroups != null).getStringDefinition());
            ydb.execute("insert into " + TABLE_NAME + " select * from " + REC_STREAM_NAME);
        } catch (ParseException | StreamSqlException e) {
            throw new InitException(e);
        } catch (Exception e) {
            throw new InitException("Cannot initialize reception-time index", e);
        }

        StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
        if (config.containsKey("streams")) {
            List<String> streamNames = config.getList("streams");
            for (String sn : streamNames) {
                TmStreamConfigEntry sce = sc.getTmEntry(sn);
                if (sce == null) {
                    throw new ConfigurationException("No stream config found for '" + sn + "'");
                }
                createRecorder(sce);
            }
        } else {
            List<TmStreamConfigEntry> sceList = sc.getTmEntries();
            for (TmStreamConfigEntry sce : sceList) {
                createRecorder(sce);
            }
        }

        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    private static TupleDefinition getRecordedTupleDefinition(boolean withGroup) {
        if (!withGroup) {
            return RECORDED_TM_TUPLE_DEFINITION;
        }
        TupleDefinition tdef = RECORDED_TM_TUPLE_DEFINITION.copy();
        tdef.addColumn(TmPartitionGroups.PGROUP_COLUMN, DataType.ENUM);
        return tdef;
    }

    private static String getPrimaryKey(boolean partitionByPname) {
        if (partitionByPname) {
            // the pname is implicitly part of the key by being a partition
            return "primary key(gentime, seqNum)";
        } else {
            // add the pname to the key to keep the same uniqueness as with the pname partitioning
            return "primary key(gentime, seqNum, pname)";
        }
    }

    private static String getPartitionBy(TimePartitionSchema timePart, boolean partitionByPname,
            boolean partitionByGroup) {
        String valueColumn = partitionByPname ? PNAME_COLUMN
                : partitionByGroup ? TmPartitionGroups.PGROUP_COLUMN : null;
        if (valueColumn == null) {
            return timePart == null ? "" : "partition by time(gentime('" + timePart.getName() + "'))";
        } else {
            return timePart == null ? "partition by value(" + valueColumn + ")"
                    : "partition by time_and_value(gentime('" + timePart.getName() + "'), " + valueColumn + ")";
        }
    }

    /**
     * Returns the partition groups or null if the tm table is not partitioned by group.
     */
    public TmPartitionGroups getPartitionGroups() {
        return partitionGroups;
    }

    private void createRecorder(TmStreamConfigEntry streamConf) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        SequenceContainer rootsc = streamConf.getRootContainer();
        if (rootsc == null) {
            rootsc = mdb.getRootSequenceContainer();
        }
        if (rootsc == null) {
            throw new ConfigurationException(
                    "MDB does not have a root sequence container and no container was specified for decoding packets from "
                            + streamConf.getName() + " stream");
        }

        Stream inputStream = ydb.getStream(streamConf.getName());

        if (inputStream == null) {
            throw new ConfigurationException("Cannot find stream '" + streamConf.getName() + "'");
        }
        Stream stream = ydb.getStream(REC_STREAM_NAME);
        StreamRecorder recorder = new StreamRecorder(inputStream, stream, rootsc, streamConf.isAsync());
        recorders.add(recorder);
    }

    @Override
    protected void doStart() {
        for (StreamRecorder sr : recorders) {
            sr.inputStream.addSubscriber(sr);
            if (sr.async) {
                new Thread(sr).start();
            }
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (StreamRecorder sr : recorders) {
            sr.quit();
            sr.inputStream.removeSubscriber(sr);
        }
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        Stream s = ydb.getStream(REC_STREAM_NAME);
        s.close();
        Utils.closeTableWriters(ydb, Arrays.asList(REC_STREAM_NAME));
        notifyStopped();
    }

    public long getNumProcessedPackets() {
        return totalNumPackets;
    }

    /**
     * Records telemetry from one stream. The decoding starts with the specified sequence container
     * 
     * If async is set to true, the tuples are put in a queue and processed from a different thread.
     * 
     * @author nm
     *
     */
    class StreamRecorder implements StreamSubscriber, Runnable {
        SequenceContainer rootSequenceContainer;
        boolean async;
        Stream inputStream;
        Stream outputStream;

        LinkedBlockingQueue<Tuple> tmQueue;
        XtceTmExtractor tmExtractor;

        StreamRecorder(Stream inputStream, Stream outputStream, SequenceContainer sc, boolean async) {
            this.outputStream = outputStream;
            this.inputStream = inputStream;
            this.rootSequenceContainer = sc;
            this.async = async;
            if (async) {
                tmQueue = new LinkedBlockingQueue<>(100000);
            }
            var pdata = new ProcessorData(yamcsInstance, "XTCEPROC", mdb, new ProcessorConfig(),
                    Collections.emptyMap());
            tmExtractor = new XtceTmExtractor(mdb, pdata);

            // we do not want to get the containers which are included via container entry
            // we only want the inherited from the root
            tmExtractor.getOptions().setSubcontainerPartOfResult(false);

            subscribeContainers(rootSequenceContainer);
        }

        /**
         * Only called if running in async mode, otherwise tuples are saved directly
         */
        @Override
        public void run() {
            Thread.currentThread().setName(this.getClass().getSimpleName() + "[" + yamcsInstance + "]");
            try {
                Tuple t;
                while (true) {
                    t = tmQueue.take();
                    if (t == END_MARK) {
                        break;
                    }
                    saveTuple(t);
                }
            } catch (InterruptedException e) {
                log.warn("Got InteruptedException when waiting for the next tuple ", e);
                Thread.currentThread().interrupt();
            }
        }

        // subscribe all containers that have useAsPacketName set
        private void subscribeContainers(SequenceContainer sc) {
            if (sc == null) {
                return;
            }

            if (sc.useAsPacketName()) {
                tmExtractor.startProviding(sc);
            }

            if (mdb.getInheritingContainers(sc) != null) {
                for (SequenceContainer sc1 : mdb.getInheritingContainers(sc)) {
                    subscribeContainers(sc1);
                }
            }
        }

        @Override
        public void onTuple(Stream istream, Tuple t) {
            int status = (Integer) t.getColumn(3);
            if ((status & TmPacket.STATUS_MASK_DO_NOT_ARCHIVE) != 0) {
                log.trace("Dropping tm tuple {} because the no archive flag is set", t);
                return;
            }
            try {
                if (async) {
                    tmQueue.put(t);
                } else {
                    synchronized (this) {
                        saveTuple(t);
                    }
                }
            } catch (InterruptedException e) {
                log.warn("Got interrupted exception while putting data in the queue");
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void streamClosed(Stream istream) {
            // shouldn't happen
            log.error("stream {} closed", istream);
        }

        public void quit() {
            if (!async) {
                return;
            }

            try {
                tmQueue.put(END_MARK);
            } catch (InterruptedException e) {
                log.warn("got interrupted while putting the empty buffer in the queue");
                Thread.currentThread().interrupt();
            }
        }

        /**
         * saves a TM tuple. The definition is in {@link StandardTupleDefinitions#TM}
         * 
         * it finds the XTCE names and puts them inside the recording
         * 
         * @param t
         */
        protected void saveTuple(Tuple t) {
            long gentime = (Long) t.getColumn(0);
            byte[] packet = (byte[]) t.getColumn(4);
            int seqCount = (Integer) t.getColumn(1);

            totalNumPackets++;

            ContainerProcessingResult cpr = tmExtractor.processPacket(packet, gentime, timeService.getMissionTime(),
                    seqCount, rootSequenceContainer);

            String pname = derivePacketName(cpr);

            try {
                List<?> c = t.getColumns();
                List<Object> columns = new ArrayList<>(c.size() + 1);
                columns.addAll(c);

                columns.add(c.size(), pname);
                TupleDefinition tdef = t.getDefinition().copy();
                tdef.addColumn(PNAME_COLUMN, DataType.ENUM);

                Tuple tp = new Tuple(tdef, columns);

                // If provided on the tuple (set by a preprocessor), this has more priority
                // in determining the pname.
                String rootContainer = tp.removeColumn(TM_ROOT_CONTAINER_COLUMN);
                if (rootContainer != null) {
                    tp.setColumn(PNAME_COLUMN, rootContainer);
                }
                if (partitionGroups != null) {
                    tp.addColumn(TmPartitionGroups.PGROUP_COLUMN, DataType.ENUM,
                            partitionGroups.getGroup(tp.getColumn(PNAME_COLUMN)));
                }

                outputStream.emitTuple(tp);
            } catch (Exception e) {
                log.error("got exception when saving packet ", e);
            }
        }
    }

    /**
     * Derives the packet name (the value of the pname column) from the result of processing a packet.
     * <p>
     * The first container is the root container. We take the name of the most specific one derived directly from the
     * root, having the {@link SequenceContainer#useAsPacketName()} flag set. If none has the flag set, the name of the
     * root container is used.
     */
    static public String derivePacketName(ContainerProcessingResult cpr) {
        List<ContainerExtractionResult> cerList = cpr.getContainerResult();
        ContainerExtractionResult root = cerList.get(0);

        String pname = null;
        for (int i = cerList.size() - 1; i >= 0; i--) {
            ContainerExtractionResult cer = cerList.get(i);
            if (cer.isDerivedFromRoot()) {
                SequenceContainer sc = cer.getContainer();
                if (sc.useAsPacketName()) {
                    pname = sc.getQualifiedName();
                    break;
                }
            }
        }
        if (pname == null) {
            pname = root.getContainer().getQualifiedName();
        }

        return pname;
    }

    /**
     * @deprecated use {@link #derivePacketName(ContainerProcessingResult)}
     */
    @Deprecated
    static public String deriveArchivePartition(ContainerProcessingResult cpr) {
        return derivePacketName(cpr);
    }

}
