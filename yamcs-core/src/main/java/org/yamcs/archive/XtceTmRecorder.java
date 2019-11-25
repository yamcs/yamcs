package org.yamcs.archive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.time.TimeService;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmExtractor;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Records XTCE TM sequence containers.
 * 
 * It creates a stream for each sequence container under the root.
 * 
 * @author nm
 *
 */
public class XtceTmRecorder extends AbstractYamcsService {

    static public String REALTIME_TM_STREAM_NAME = "tm_realtime";
    static public String DUMP_TM_STREAM_NAME = "tm_dump";
    static public final String TABLE_NAME = "tm";
    static public final String PNAME_COLUMN = "pname";

    static public final TupleDefinition RECORDED_TM_TUPLE_DEFINITION;
    static {
        RECORDED_TM_TUPLE_DEFINITION = StandardTupleDefinitions.TM.copy();
        RECORDED_TM_TUPLE_DEFINITION.addColumn(PNAME_COLUMN, DataType.ENUM); // container name (XTCE qualified name)
    }

    private long totalNumPackets;

    final Tuple END_MARK = new Tuple(StandardTupleDefinitions.TM,
            new Object[] { null, null, null, null });

    XtceDb xtceDb;

    private final List<StreamRecorder> recorders = new ArrayList<>();

    TimeService timeService;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("streams", OptionType.LIST).withElementType(OptionType.STRING);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        super.init(yamcsInstance, config);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            if (ydb.getTable(TABLE_NAME) == null) {
                String query = "create table " + TABLE_NAME + "(" + RECORDED_TM_TUPLE_DEFINITION.getStringDefinition1()
                        + ", primary key(gentime, seqNum)) histogram(pname) partition by time_and_value(gentime"
                        + getTimePartitioningSchemaSql() + ", pname) table_format=compressed";

                ydb.execute(query);
            }
            ydb.execute("create stream tm_is" + RECORDED_TM_TUPLE_DEFINITION.getStringDefinition());
            ydb.execute("insert into " + TABLE_NAME + " select * from tm_is");
        } catch (ParseException | StreamSqlException e) {
            throw new InitException(e);
        }
        xtceDb = XtceDbFactory.getInstance(yamcsInstance);

        StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
        if (!config.containsKey("streams")) {
            List<StreamConfigEntry> sceList = sc.getEntries(StandardStreamType.tm);
            for (StreamConfigEntry sce : sceList) {
                createRecorder(sce);
            }
        } else if (config.containsKey("streams")) {
            List<String> streamNames = config.getList("streams");
            for (String sn : streamNames) {
                StreamConfigEntry sce = sc.getEntry(StandardStreamType.tm, sn);
                if (sce == null) {
                    throw new ConfigurationException("No stream config found for '" + sn + "'");
                }
                createRecorder(sce);
            }
        }

        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    static String getTimePartitioningSchemaSql() {
        YConfiguration yconfig = YamcsServer.getServer().getConfig();
        String partSchema = "";
        if (yconfig.containsKey("archiveConfig", "timePartitioningSchema")) {
            partSchema = "('" + yconfig.getSubString("archiveConfig", "timePartitioningSchema") + "')";
        }
        return partSchema;
    }

    private void createRecorder(StreamConfigEntry streamConf) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        SequenceContainer rootsc = streamConf.getRootContainer();
        if (rootsc == null) {
            rootsc = xtceDb.getRootSequenceContainer();
        }
        if (rootsc == null) {
            throw new ConfigurationException(
                    "XtceDb does not have a root sequence container and no container was specified for decoding packets from "
                            + streamConf.getName() + " stream");
        }

        Stream inputStream = ydb.getStream(streamConf.getName());

        if (inputStream == null) {
            throw new ConfigurationException("Cannot find stream '" + streamConf.getName() + "'");
        }
        Stream tm_is = ydb.getStream("tm_is");
        StreamRecorder recorder = new StreamRecorder(inputStream, tm_is, rootsc, streamConf.isAsync());
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
        Stream s = ydb.getStream("tm_is");
        Collection<StreamSubscriber> subscribers = s.getSubscribers();
        s.close();
        for (StreamSubscriber ss : subscribers) {
            if (ss instanceof TableWriter) {
                ((TableWriter) ss).close();
            }
        }
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
            tmExtractor = new XtceTmExtractor(xtceDb);
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

        // subscribe all containers that have useAsArchivePartition set
        private void subscribeContainers(SequenceContainer sc) {
            if (sc == null) {
                return;
            }

            if (sc.useAsArchivePartition()) {
                tmExtractor.startProviding(sc);
            }

            if (xtceDb.getInheritingContainers(sc) != null) {
                for (SequenceContainer sc1 : xtceDb.getInheritingContainers(sc)) {
                    subscribeContainers(sc1);
                }
            }
        }

        @Override
        public void onTuple(Stream istream, Tuple t) {
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
         * saves a TM tuple. The definition is in * TmProviderAdapter
         * 
         * it finds the XTCE names and puts them inside the recording
         * 
         * @param t
         */
        protected void saveTuple(Tuple t) {
            long gentime = (Long) t.getColumn(0);
            byte[] packet = (byte[]) t.getColumn(3);
            totalNumPackets++;

            tmExtractor.processPacket(packet, gentime, timeService.getMissionTime(), rootSequenceContainer);
            String packetName = tmExtractor.getPacketName();

            try {
                List<Object> c = t.getColumns();
                List<Object> columns = new ArrayList<>(c.size() + 1);
                columns.addAll(c);

                columns.add(c.size(), packetName);
                TupleDefinition tdef = t.getDefinition().copy();
                tdef.addColumn(PNAME_COLUMN, DataType.ENUM);

                Tuple tp = new Tuple(tdef, columns);
                outputStream.emitTuple(tp);
            } catch (Exception e) {
                log.error("got exception when saving packet ", e);
            }
        }
    }
}
