package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.MediaType;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractPacketsApi;
import org.yamcs.protobuf.ExportPacketsRequest;
import org.yamcs.protobuf.GetPacketRequest;
import org.yamcs.protobuf.ListPacketNamesRequest;
import org.yamcs.protobuf.ListPacketNamesResponse;
import org.yamcs.protobuf.ListPacketsRequest;
import org.yamcs.protobuf.ListPacketsResponse;
import org.yamcs.protobuf.StreamPacketsRequest;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
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
import com.google.gson.Gson;
import com.google.protobuf.ByteString;

public class PacketsApi extends AbstractPacketsApi<Context> {

    @Override
    public void listPacketNames(Context ctx, ListPacketNamesRequest request,
            Observer<ListPacketNamesResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ListPacketNamesResponse.Builder responseb = ListPacketNamesResponse.newBuilder();
        TableDefinition tableDefinition = ydb.getTable(XtceTmRecorder.TABLE_NAME);
        if (tableDefinition == null) {
            observer.complete(responseb.build());
            return;
        }

        BiMap<String, Short> enumValues = tableDefinition.getEnumValues(XtceTmRecorder.PNAME_COLUMN);
        if (enumValues != null) {
            List<String> unsortedPackets = new ArrayList<>();
            for (Entry<String, Short> entry : enumValues.entrySet()) {
                String packetName = entry.getKey();
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, packetName)) {
                    unsortedPackets.add(packetName);
                }
            }
            Collections.sort(unsortedPackets);
            responseb.addAllName(unsortedPackets);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listPackets(Context ctx, ListPacketsRequest request, Observer<ListPacketsResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");

        ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadPacket, request.getNameList());
        Set<String> nameSet = new HashSet<>(request.getNameList());
        if (nameSet.isEmpty()) {
            for (String packetName : getTmPacketNames(instance, ctx.user)) {
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, packetName)) {
                    nameSet.add(packetName);
                }
            }
        }
        if (nameSet.isEmpty()) {
            // No permissions for any packet
            observer.complete(ListPacketsResponse.getDefaultInstance());
            return;
        }

        PacketPageToken nextToken = null;
        if (request.hasNext()) {
            String next = request.getNext();
            nextToken = PacketPageToken.decode(next);
        }

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
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
        sqlb.limit(pos, limit + 1l); // one more to detect hasMore

        ListPacketsResponse.Builder responseb = ListPacketsResponse.newBuilder();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

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
                    PacketPageToken token = new PacketPageToken(
                            TimeEncoding.fromProtobufTimestamp(last.getGenerationTime()),
                            last.getSequenceNumber());
                    responseb.setContinuationToken(token.encodeAsString());
                }
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void getPacket(Context ctx, GetPacketRequest request, Observer<TmPacketData> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        long gentime = request.getGentime();
        int seqNum = request.getSeqnum();

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME)
                .where("gentime = ?", gentime)
                .where("seqNum = ?", seqNum);

        List<TmPacketData> packets = new ArrayList<>();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, pdata.getId().getName())) {
                    packets.add(pdata);
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (packets.isEmpty()) {
                    observer.completeExceptionally(
                            new NotFoundException("No packet for id (" + gentime + ", " + seqNum + ")"));
                } else if (packets.size() > 1) {
                    observer.completeExceptionally(new InternalServerErrorException("Too many results"));
                } else {
                    observer.complete(packets.get(0));
                }
            }
        });
    }

    @Override
    public void streamPackets(Context ctx, StreamPacketsRequest request, Observer<TmPacketData> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadPacket, request.getNameList());

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.getNameCount() > 0) {
            sqlb.whereColIn("pname", request.getNameList());
        }

        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, pdata.getId().getName())) {
                    observer.next(pdata);
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        });
    }

    @Override
    public void exportPackets(Context ctx, ExportPacketsRequest request, Observer<HttpBody> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        Set<String> nameSet = new HashSet<>(request.getNameList());
        ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadPacket, nameSet);

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.getNameCount() > 0) {
            sqlb.whereColIn("pname", nameSet);
        }
        String sql = sqlb.toString();

        HttpBody metadata = HttpBody.newBuilder()
                .setContentType(MediaType.OCTET_STREAM.toString())
                .setFilename("packets.raw")
                .build();
        observer.next(metadata);

        StreamFactory.stream(instance, sql, sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (observer.isCancelled()) {
                    stream.close();
                    return;
                }

                byte[] raw = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
                HttpBody body = HttpBody.newBuilder()
                        .setData(ByteString.copyFrom(raw))
                        .build();
                observer.next(body);
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        });
    }

    /**
     * Get packet names this user has appropriate privileges for.
     */
    private Collection<String> getTmPacketNames(String yamcsInstance, User user) {
        XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
        ArrayList<String> tl = new ArrayList<>();
        for (SequenceContainer sc : xtcedb.getSequenceContainers()) {
            if (user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, sc.getQualifiedName())) {
                tl.add(sc.getQualifiedName());
            }
        }
        return tl;
    }

    /**
     * Stateless continuation token for paged requests on the tm table
     */
    private static class PacketPageToken {

        public long gentime;
        public int seqNum;

        public PacketPageToken(long timestamp, int seqNum) {
            this.gentime = timestamp;
            this.seqNum = seqNum;
        }

        public static PacketPageToken decode(String encoded) {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded));
            return new Gson().fromJson(decoded, PacketPageToken.class);
        }

        public String encodeAsString() {
            String json = new Gson().toJson(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        }
    }
}
