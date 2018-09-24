package org.yamcs.web.rest.archive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.ConfigurationException;
import org.yamcs.api.MediaType;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.protobuf.Archive.GetPacketNamesResponse;
import org.yamcs.protobuf.Rest.ListPacketsResponse;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.collect.BiMap;

import io.netty.buffer.ByteBuf;

public class ArchivePacketRestHandler extends RestHandler {

    @Route(path = "/api/archive/:instance/packet-names", method = "GET")
    public void listPacketNames(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        GetPacketNamesResponse.Builder responseb = GetPacketNamesResponse.newBuilder();
        TableDefinition tableDefinition = ydb.getTable(XtceTmRecorder.TABLE_NAME);
        if (tableDefinition == null) {
            completeOK(req, responseb.build());
            return;
        }

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

    @Route(path = "/api/archive/:instance/packets/:gentime?", method = "GET")
    public void listPackets(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        boolean desc = req.asksDescending(true);

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

        PacketPageToken nextToken = null;
        if (req.hasQueryParameter("next")) {
            String next = req.getQueryParameter("next");
            nextToken = PacketPageToken.decode(next);
        }

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();

        // Query optimization to skip previously outputted pages at the Rocks level.
        // (because the gentime/seqnum condition used further down is unoptimized)
        if (nextToken != null) {
            if (desc) {
                ir.setStop(nextToken.gentime, true);
            } else {
                ir.setStart(nextToken.gentime, true);
            }
        }
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (req.hasRouteParam("gentime")) {
            sqlb.where("gentime = ?", req.getDateRouteParam("gentime"));
        }
        if (!nameSet.isEmpty()) {
            sqlb.whereColIn("pname", nameSet);
        }
        if (nextToken != null) {
            if (desc) {
                sqlb.where("(gentime < ? or (gentime = ? and seqNum < ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            } else {
                sqlb.where("(gentime > ? or (gentime = ? and seqNum > ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            }
        }

        sqlb.descend(desc);
        sqlb.limit(pos, limit);

        if (req.asksFor(MediaType.OCTET_STREAM)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(),
                    new StreamSubscriber() {

                        @Override
                        public void onTuple(Stream stream, Tuple tuple) {
                            TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                            buf.writeBytes(pdata.getPacket().toByteArray());
                        }

                        @Override
                        public void streamClosed(Stream stream) {
                            completeOK(req, MediaType.OCTET_STREAM, buf);
                        }
                    });
        } else {
            sqlb.limit(pos, limit + 1); // one more to detect hasMore

            ListPacketsResponse.Builder responseb = ListPacketsResponse.newBuilder();
            RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(),
                    new StreamSubscriber() {

                        TmPacketData last;
                        int count;

                        @Override
                        public void onTuple(Stream stream, Tuple tuple) {
                            if (++count <= limit) {
                                TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                                responseb.addPacket(pdata);
                                last = pdata;
                            }
                        }

                        @Override
                        public void streamClosed(Stream stream) {
                            if (count > limit) {
                                PacketPageToken token = new PacketPageToken(last.getGenerationTime(),
                                        last.getSequenceNumber());
                                responseb.setContinuationToken(token.encodeAsString());
                            }
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
                .where("gentime = ?", gentime)
                .where("seqNum = ?", seqNum);

        List<TmPacketData> packets = new ArrayList<>();
        RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                if (hasObjectPrivilege(req, ObjectPrivilegeType.ReadPacket, pdata.getId().getName())) {
                    packets.add(pdata);
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (packets.isEmpty()) {
                    completeWithError(req,
                            new NotFoundException(req, "No packet for id (" + gentime + ", " + seqNum + ")"));
                } else if (packets.size() > 1) {
                    completeWithError(req, new InternalServerErrorException("Too many results"));
                } else {
                    completeOK(req, packets.get(0));
                }
            }
        });
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
