package org.yamcs.web.rest.archive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.api.MediaType;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.IndexRequestListener;
import org.yamcs.archive.IndexServer;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.protobuf.Archive.GetPacketNamesResponse;
import org.yamcs.protobuf.Archive.IndexEntry;
import org.yamcs.protobuf.Archive.IndexGroup;
import org.yamcs.protobuf.Archive.IndexResponse;
import org.yamcs.protobuf.Rest.ListPacketsResponse;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.IndexRequest;
import org.yamcs.protobuf.Yamcs.IndexResult;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.RestStreamSubscriber;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.collect.BiMap;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;

public class ArchivePacketRestHandler extends RestHandler {

    @Route(path = "/api/archive/:instance/packet-names", method = "GET")
    public void listPacketNames(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        GetPacketNamesResponse.Builder responseb = GetPacketNamesResponse.newBuilder();
        TableDefinition tableDefinition = ydb.getTable(XtceTmRecorder.TABLE_NAME);
        BiMap<String, Short> enumValues = tableDefinition.getEnumValues(XtceTmRecorder.PNAME_COLUMN);
        if (enumValues != null) {
            List<String> unsortedPackets = new ArrayList<>();
            for (Entry<String, Short> entry : enumValues.entrySet()) {
                String packetName = entry.getKey();
                if (hasObjectPrivilege(req, ObjectPrivilegeType.ReadPacket, packetName)) {
                    unsortedPackets.add(packetName);
                }
            }
            Collections.sort(unsortedPackets);
            responseb.addAllName(unsortedPackets);
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/archive/:instance/packet-index", method = "GET")
    public void listPacketIndex(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        IndexServer indexServer = verifyIndexServer(req, instance);

        int limit = req.getQueryParameterAsInt("limit", 100);
        int mergeTime = req.getQueryParameterAsInt("mergeTime", 2000);

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasStart()) {
            requestb.setStart(ir.getStart());
        }
        if (ir.hasStop()) {
            requestb.setStop(ir.getStop());
        }

        if (req.hasQueryParameter("name")) {
            for (String names : req.getQueryParameterList("name")) {
                for (String name : names.split(",")) {
                    requestb.addTmPacket(NamedObjectId.newBuilder().setName(name.trim()));
                }
            }
        }
        if (requestb.getTmPacketCount() == 0) {
            requestb.setSendAllTm(true);
        }

        try {
            IndexResponse.Builder responseb = IndexResponse.newBuilder();
            Map<NamedObjectId, IndexGroup.Builder> groupBuilders = new HashMap<>();
            indexServer.submitIndexRequest(requestb.build(), new IndexRequestListener() {

                int count = 0;

                @Override
                public void processData(IndexResult indexResult) {
                    if (count < limit) {
                        for (ArchiveRecord rec : indexResult.getRecordsList()) {
                            if (count < limit) {
                                IndexGroup.Builder groupb = groupBuilders.get(rec.getId());
                                if (groupb == null) {
                                    groupb = IndexGroup.newBuilder().setId(rec.getId());
                                    groupBuilders.put(rec.getId(), groupb);
                                }
                                groupb.addEntry(IndexEntry.newBuilder()
                                        .setStart(TimeEncoding.toString(rec.getFirst()))
                                        .setStop(TimeEncoding.toString(rec.getLast()))
                                        .setCount(rec.getNum()));
                                count++;
                            }
                        }
                    }
                }

                @Override
                public void finished(boolean success) {
                    if (success) {
                        List<IndexGroup.Builder> sortedGroups = new ArrayList<>(groupBuilders.values());
                        Collections.sort(sortedGroups, (g1, g2) -> {
                            return g1.getId().getName().compareTo(g2.getId().getName());
                        });
                        sortedGroups.forEach(groupb -> responseb.addGroup(groupb));
                        completeOK(req, responseb.build());
                    } else {
                        sendRestError(req, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                new InternalServerErrorException("Too many results"));
                    }
                }
            });
        } catch (YamcsException e) {
            throw new InternalServerErrorException("Too many results", e);
        }
    }

    @Route(path = "/api/archive/:instance/packets/:gentime?", method = "GET")
    public void listPackets(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);

        Set<String> nameSet = new HashSet<>();
        for (String names : req.getQueryParameterList("name", Collections.emptyList())) {
            for (String name : names.split(",")) {
                nameSet.add(name.trim());
            }
        }
        if (!nameSet.isEmpty()) {
            checkObjectPrivileges(req, ObjectPrivilegeType.ReadPacket, nameSet);
        } else {
            nameSet.addAll(getTmPacketNames(instance, req.getUser()));
        }

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (req.hasRouteParam("gentime")) {
            sqlb.where("gentime = ?", req.getDateRouteParam("gentime"));
        }
        if (!nameSet.isEmpty()) {
            sqlb.whereColIn("pname", nameSet);
        }
        sqlb.descend(req.asksDescending(true));

        if (req.asksFor(MediaType.OCTET_STREAM)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(),
                    new RestStreamSubscriber(pos, limit) {
                        @Override
                        public void processTuple(Stream stream, Tuple tuple) {
                            TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                            buf.writeBytes(pdata.getPacket().toByteArray());
                        }

                        @Override
                        public void streamClosed(Stream stream) {
                            completeOK(req, MediaType.OCTET_STREAM, buf);
                        }
                    });
        } else {
            ListPacketsResponse.Builder responseb = ListPacketsResponse.newBuilder();
            RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(),
                    new RestStreamSubscriber(pos, limit) {

                        @Override
                        public void processTuple(Stream stream, Tuple tuple) {
                            TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                            responseb.addPacket(pdata);
                        }

                        @Override
                        public void streamClosed(Stream stream) {
                            completeOK(req, responseb.build());
                        }
                    });
        }
    }

    @Route(path = "/api/archive/:instance/packets/:gentime/:seqnum", method = "GET")
    public void getPacket(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        long gentime = req.getDateRouteParam("gentime");
        int seqNum = req.getIntegerRouteParam("seqnum");

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME)
                .where("gentime = ?", gentime).where("seqNum = ?", seqNum);

        List<TmPacketData> packets = new ArrayList<>();
        RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new RestStreamSubscriber(0, 2) {
            @Override
            public void processTuple(Stream stream, Tuple tuple) {
                TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                if (hasObjectPrivilege(req, ObjectPrivilegeType.ReadPacket, pdata.getId().getName())) {
                    packets.add(pdata);
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (packets.isEmpty()) {
                    sendRestError(req, HttpResponseStatus.NOT_FOUND,
                            new NotFoundException(req, "No packet for id (" + gentime + ", " + seqNum + ")"));
                } else if (packets.size() > 1) {
                    sendRestError(req, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            new InternalServerErrorException("Too many results"));
                } else {
                    completeOK(req, packets.get(0));
                }

            }
        });
    }

    private IndexServer verifyIndexServer(RestRequest req, String instance) throws HttpException {
        verifyInstance(req, instance);
        IndexServer indexServer = YamcsServer.getService(instance, IndexServer.class);
        if (indexServer == null) {
            throw new BadRequestException("Index service not enabled for instance '" + instance + "'");
        } else {
            return indexServer;
        }
    }

    /**
     * Get packet names this user has appropriate privileges for.
     */
    public Collection<String> getTmPacketNames(String yamcsInstance, User user)
            throws ConfigurationException {
        XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
        ArrayList<String> tl = new ArrayList<>();
        for (SequenceContainer sc : xtcedb.getSequenceContainers()) {
            if (user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, sc.getQualifiedName())) {
                tl.add(sc.getQualifiedName());
            }
        }
        return tl;
    }
}
