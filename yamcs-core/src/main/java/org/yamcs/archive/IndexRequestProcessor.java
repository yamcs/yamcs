package org.yamcs.archive;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.archive.IndexRequestListener.IndexType;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.mdb.Mdb;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.HistogramIterator;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;

/**
 * Performs histogram and completeness index retrievals.
 * 
 * @author nm
 *
 */
public class IndexRequestProcessor implements Runnable {
    static final SecureRandom random = new SecureRandom();

    final String yamcsInstance;
    final static AtomicInteger counter = new AtomicInteger();
    static Logger log = LoggerFactory.getLogger(IndexRequestProcessor.class.getName());
    final IndexRequest req;
    TmIndexService tmIndexer;
    IndexRequestListener indexRequestListener;

    private static Cache<String, TokenData> tokenCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.SECONDS).maximumSize(1000).build();

    // these maps contains the names with which the records will be sent to the client
    Map<String, NamedObjectId> tmpackets;
    Map<String, NamedObjectId> eventSources;
    Map<String, NamedObjectId> commands;
    Map<String, NamedObjectId> ppGroups;
    Map<String, NamedObjectId> completenessGroups;

    boolean sendTms;
    int batchSize = 500;
    int limit = -1;

    String token;
    TokenData tokenData = null;
    int count = 0;
    HistoRequest[] hreq = new HistoRequest[5];
    MergingResult mergingResult;

    public IndexRequestProcessor(TmIndexService tmIndexer, IndexRequest req, int limit, String recToken,
            IndexRequestListener l) {
        log.debug("new index request: {}", req);
        this.yamcsInstance = req.getInstance();
        this.req = req;
        this.tmIndexer = tmIndexer;
        this.indexRequestListener = l;
        this.limit = limit;

        if (recToken != null) {
            tokenData = tokenCache.getIfPresent(recToken);
            if (tokenData == null) {
                throw new InvalidTokenException();
            }
            tokenCache.invalidate(recToken);
            this.token = recToken;
        }

        if (req.isSendAllTm() || req.getTmPackets().size() > 0) {
            sendTms = true;
            Mdb mdb = MdbFactory.getInstance(yamcsInstance);

            if (req.isSendAllTm()) {
                if (req.getDefaultNamespace() != null) {
                    String defaultns = req.getDefaultNamespace();
                    tmpackets = new HashMap<>();
                    for (SequenceContainer sc : mdb.getSequenceContainers()) {
                        if (sc.getAlias(defaultns) != null) {
                            tmpackets.put(sc.getQualifiedName(), NamedObjectId.newBuilder()
                                    .setName(sc.getAlias(defaultns)).setNamespace(defaultns).build());
                        }
                    }
                }
            } else {
                tmpackets = new HashMap<>();
                for (NamedObjectId id : req.getTmPackets()) {
                    SequenceContainer sc = mdb.getSequenceContainer(id);
                    if (sc != null) {
                        tmpackets.put(sc.getQualifiedName(), id);
                    }
                }
            }
            int mergeTime = (req.getMergeTime() > 0 ? req.getMergeTime() : 2000);
            hreq[0] = new HistoRequest(XtceTmRecorder.TABLE_NAME, XtceTmRecorder.PNAME_COLUMN, mergeTime,
                    tmpackets);
        }

        if (req.isSendAllEvent() || req.getEventSources().size() > 0) {
            eventSources = new HashMap<>();
            for (NamedObjectId id : req.getEventSources()) {
                eventSources.put(id.getName(), id);
            }
            int mergeTime = (req.getMergeTime() > 0 ? req.getMergeTime() : 2000);
            hreq[1] = new HistoRequest(EventRecorder.TABLE_NAME, "source", mergeTime, eventSources);
        }

        if (req.isSendAllCmd() || req.getCommandNames().size() > 0) {
            commands = new HashMap<>();
            for (NamedObjectId id : req.getCommandNames()) {
                commands.put(id.getName(), id);
            }
            int mergeTime = (req.getMergeTime() > 0 ? req.getMergeTime() : 2000);
            hreq[2] = new HistoRequest(CommandHistoryRecorder.TABLE_NAME,
                    StandardTupleDefinitions.CMDHIST_TUPLE_COL_CMDNAME, mergeTime, commands);
        }

        if (req.isSendAllPp() || req.getPpGroups().size() > 0) {
            ppGroups = new HashMap<>();
            for (NamedObjectId id : req.getPpGroups()) {
                ppGroups.put(id.getName(), id);
            }
            // use 20 sec for the PP to avoid millions of records
            int mergeTime = (req.getMergeTime() > 0 ? req.getMergeTime() : 20000);
            hreq[3] = new HistoRequest(ParameterRecorder.TABLE_NAME,
                    StandardTupleDefinitions.PARAMETER_COL_GROUP, mergeTime, ppGroups);
        }

        if (req.isSendCompletenessIndex() || req.getCompletenessGroups().size() > 0) {
            if (tmIndexer == null) {
                throw new IllegalArgumentException("TmIndexer cannot be null if completeness is requested");
            }
            completenessGroups = new HashMap<>();
            for (NamedObjectId id : req.getCompletenessGroups()) {
                completenessGroups.put(id.getName(), id);
            }
            int mergeTime = (req.getMergeTime() > 0 ? req.getMergeTime() : -1);
            hreq[4] = new HistoRequest(null, null, mergeTime, completenessGroups);
        }

        if (tokenData != null) {
            for (int i = 0; i < tokenData.lastHistoId; i++) {
                hreq[i] = null;
            }
            HistoRequest hr = hreq[tokenData.lastHistoId];
            if (hr != null) {
                hr.seekTime = tokenData.lastTime + 1;
                hr.seekValue = tokenData.lastName;
                hr.seekId = tokenData.lastId;
            }
            token = getRandomToken();
            tokenCache.put(token, tokenData);
        } else if (limit > 0) {
            tokenData = new TokenData();
            token = getRandomToken();
            tokenCache.put(token, tokenData);
        }

    }

    private static String getRandomToken() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    @Override
    public void run() {
        boolean ok = true;
        boolean cont = true;
        try {
            if (tokenData != null) {
                mergingResult = tokenData.mergingResult;
            } else {
                mergingResult = new MergingResult();
            }

            for (int i = 0; i < 5; i++) {
                if (cont && hreq[i] != null) {
                    if (tokenData != null) {
                        tokenData.lastHistoId = i;
                    }
                    if (i < 4) {
                        indexRequestListener.begin(IndexType.HISTOGRAM, hreq[i].tblName);
                        cont = sendHistogramData(hreq[i]);
                    } else {
                        indexRequestListener.begin(IndexType.COMPLETENESS, null);
                        cont = sendCompletenessIndex(hreq[4]);
                    }
                    if (cont) {
                        cont = flushMergingResult();
                        mergingResult = new MergingResult();
                        if (tokenData != null) {
                            tokenData.mergingResult = mergingResult;
                        }
                    }
                }
            }
            if (cont) {
                token = null;
            }
        } catch (Exception e) {
            log.warn("got exception while sending the response", e);
            ok = false;
        } finally {
            try {
                indexRequestListener.finished(ok ? token : null, ok);
            } catch (Exception e) {
                log.warn("Error when sending finished signal ", e);
            }
        }
    }

    private boolean flushMergingResult() {
        for (ArchiveRecord ar : mergingResult.res.values()) {
            indexRequestListener.processData(ar);
            count++;
            if (limit > 0 && count >= limit) {
                return false;
            }
        }
        return true;
    }

    boolean sendHistogramData(HistoRequest hreq) {
        log.debug("Sending histogram data for table {} column {}", hreq.tblName, hreq.columnName);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        TableDefinition tblDef = ydb.getTable(hreq.tblName);
        if (tblDef == null) {
            log.warn("Histogram from table '{}' requested, but table does not exist.", hreq.tblName);
            return true;
        }
        ColumnSerializer<String> histoColumnSerializer = tblDef.getColumnSerializer(hreq.columnName);
        ColumnDefinition histoColumnDefinition = tblDef.getColumnDefinition(hreq.columnName);
        TimeInterval interval = getTimeInterval(req);

        try (HistogramIterator iter = ydb.getStorageEngine(tblDef).getHistogramIterator(ydb, tblDef, hreq.columnName,
                interval)) {
            if (hreq.seekValue != null) {
                iter.seek(hreq.seekValue, hreq.seekTime);
            }

            while (iter.hasNext()) {
                HistogramRecord hr = iter.next();
                String name = histoColumnSerializer.fromByteArray(hr.getColumnv(), histoColumnDefinition);
                NamedObjectId id;
                if (hreq.name2id != null && !hreq.name2id.isEmpty()) {
                    id = hreq.name2id.get(name);
                    if (id == null) {
                        log.debug("Not sending {} because no id for it", name);
                        continue;
                    }
                } else {
                    id = NamedObjectId.newBuilder().setName(name).build();
                }
                if (tokenData != null) {
                    tokenData.lastName = hr.getColumnv();
                    tokenData.lastTime = hr.getStop();
                }

                ArchiveRecord ar = ArchiveRecord.newBuilder().setId(id)
                        .setFirst(TimeEncoding.toProtobufTimestamp(hr.getStart()))
                        .setLast(TimeEncoding.toProtobufTimestamp(hr.getStop()))
                        .setNum(hr.getNumTuples()).build();
                sendData(ar);
                if (limit > 0 && count >= limit) {
                    return false;
                }
            }

        } catch (Exception e) {
            log.error("got exception while reading histogram data", e);
            return false;
        }

        return true;
    }

    private TimeInterval getTimeInterval(IndexRequest req) {
        TimeInterval r = new TimeInterval();
        if (req.getStart() != TimeEncoding.INVALID_INSTANT) {
            r.setStart(req.getStart());
        }
        if (req.getStop() != TimeEncoding.INVALID_INSTANT) {
            r.setEnd(req.getStop());
        }
        return r;
    }

    private boolean sendCompletenessIndex(HistoRequest hreq) {
        long start = req.getStart();
        long stop = req.getStop();

        if (hreq.seekId != null) {
            start = hreq.seekTime;
        }
        IndexIterator it;

        if (hreq.name2id == null || hreq.name2id.isEmpty()) {
            it = tmIndexer.getIterator(null, start, stop);
        } else {
            List<NamedObjectId> names = new ArrayList<>(hreq.name2id.values());
            it = tmIndexer.getIterator(names, start, stop);
        }
        try {
            ArchiveRecord ar;
            while ((ar = it.getNextRecord()) != null) {
                sendData(ar);
                if (tokenData != null) {
                    tokenData.lastId = ar.getId();
                    tokenData.lastTime = TimeEncoding.fromProtobufTimestamp(ar.getLast());
                }
                if (limit > 0 && count >= limit) {
                    return false;
                }
            }
            return true;
        } finally {
            it.close();
        }
    }

    void sendData(ArchiveRecord ar) {
        if (req.getMergeTime() > 0) {
            ArchiveRecord ar1 = mergingResult.add(ar, req.getMergeTime());
            if (ar1 != null) {
                count++;
                indexRequestListener.processData(ar1);
            }
        } else {
            count++;
            indexRequestListener.processData(ar);
        }
    }

    static class MergingResult {
        Map<NamedObjectId, ArchiveRecord> res = new HashMap<>();

        public ArchiveRecord add(ArchiveRecord ar, int mergeTime) {
            ArchiveRecord ar1 = res.get(ar.getId());
            if (ar1 == null) {
                res.put(ar.getId(), ar);
                return null;
            }
            long tdelta = Durations.toMillis(Timestamps.between(ar1.getFirst(), ar.getLast()));
            if (tdelta < mergeTime) {
                ArchiveRecord ar2 = ArchiveRecord.newBuilder().setFirst(ar1.getFirst())
                        .setLast(ar.getLast()).setNum(ar1.getNum() + ar.getNum())
                        .setId(ar.getId()).build();
                res.put(ar.getId(), ar2);
                return null;
            } else {
                res.put(ar.getId(), ar);
                return ar1;
            }
        }
    }

    static class TokenData {
        int lastHistoId = 0;
        byte[] lastName;
        NamedObjectId lastId;

        long lastTime;
        MergingResult mergingResult = new MergingResult();

        @Override
        public String toString() {
            return "TokenData [lastHistoId=" + lastHistoId
                    + (lastName == null ? "" : ", lastName=" + StringConverter.arrayToHexString(lastName))
                    + (lastId == null ? "" : ", lastId=" + lastId.getName())
                    + ", lastTime=" + TimeEncoding.toString(lastTime) + "]";
        }
    }

    static class HistoRequest {
        final String tblName;
        final String columnName;
        final long mergeTime;
        final Map<String, NamedObjectId> name2id;
        byte[] seekValue;
        NamedObjectId seekId;
        long seekTime;

        public HistoRequest(String tableName, String columnName, int mergeTime,
                Map<String, NamedObjectId> name2id) {
            this.tblName = tableName;
            this.columnName = columnName;
            this.mergeTime = mergeTime;
            this.name2id = name2id;
        }
    }

    @SuppressWarnings("serial")
    static public class InvalidTokenException extends RuntimeException {
    }
}
