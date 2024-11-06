package org.yamcs.http.api;

import static org.yamcs.StandardTupleDefinitions.GENTIME_COLUMN;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.Processor;
import org.yamcs.ProcessorConfig;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.container.ContainerConsumer;
import org.yamcs.container.ContainerRequestManager;
import org.yamcs.events.AbstractEventProducer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.MediaType;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.mdb.XtceTmExtractor;
import org.yamcs.parameter.ContainerParameterValue;
import org.yamcs.protobuf.AbstractPacketsApi;
import org.yamcs.protobuf.ContainerData;
import org.yamcs.protobuf.ExportPacketRequest;
import org.yamcs.protobuf.ExportPacketsRequest;
import org.yamcs.protobuf.ExtractPacketRequest;
import org.yamcs.protobuf.ExtractPacketResponse;
import org.yamcs.protobuf.ExtractedParameterValue;
import org.yamcs.protobuf.GetPacketRequest;
import org.yamcs.protobuf.ListPacketNamesRequest;
import org.yamcs.protobuf.ListPacketNamesResponse;
import org.yamcs.protobuf.ListPacketsRequest;
import org.yamcs.protobuf.ListPacketsResponse;
import org.yamcs.protobuf.StreamPacketsRequest;
import org.yamcs.protobuf.SubscribeContainersRequest;
import org.yamcs.protobuf.SubscribePacketsRequest;
import org.yamcs.protobuf.TmPacketData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db.Event;

import com.google.common.collect.BiMap;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;

public class PacketsApi extends AbstractPacketsApi<Context> {

    @Override
    public void listPacketNames(Context ctx, ListPacketNamesRequest request,
            Observer<ListPacketNamesResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
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
            for (var entry : enumValues.entrySet()) {
                String packetName = entry.getKey();
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, packetName)) {
                    unsortedPackets.add(packetName);
                }
            }
            Collections.sort(unsortedPackets);
            responseb.addAllName(unsortedPackets);
            responseb.addAllPackets(unsortedPackets);
        }

        enumValues = tableDefinition.getEnumValues(StandardTupleDefinitions.TM_LINK_COLUMN);
        if (enumValues != null) {
            List<String> unsortedLinks = new ArrayList<>();
            for (var entry : enumValues.entrySet()) {
                String link = entry.getKey();
                unsortedLinks.add(link);
            }
            Collections.sort(unsortedLinks);
            responseb.addAllLinks(unsortedLinks);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listPackets(Context ctx, ListPacketsRequest request, Observer<ListPacketsResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");

        ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadPacket, request.getNameList());
        Set<String> nameSet = new HashSet<>(request.getNameList());
        if (nameSet.isEmpty()) {
            nameSet.addAll(getTmPacketNames(instance, ctx.user));
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
            sqlb.whereColAfterOrEqual(GENTIME_COLUMN, request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore(GENTIME_COLUMN, request.getStop());
        }

        if (!nameSet.isEmpty()) {
            sqlb.whereColIn("pname", nameSet);
        }
        if (request.hasLink()) {
            sqlb.where("link = ?", request.getLink());
        }
        if (nextToken != null) {
            if (desc) {
                sqlb.where("(gentime <= ? and (gentime < ? or seqNum < ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            } else {
                sqlb.where("(gentime >= ? and (gentime > ? or seqNum > ?))",
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
        String instance = InstancesApi.verifyInstance(request.getInstance());
        long gentime = TimeEncoding.fromProtobufTimestamp(request.getGentime());
        int seqNum = request.getSeqnum();

        var sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);
        if (request.hasPname()) { // Optional due to deprecated API where name is not provided
            sqlb = sqlb.where("pname = ?", request.getPname());
        }
        sqlb = sqlb.where("gentime = ?", gentime);
        sqlb = sqlb.where("seqNum = ?", seqNum);

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
        String instance = InstancesApi.verifyInstance(request.getInstance());

        ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadPacket, request.getNameList());

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual(GENTIME_COLUMN, request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore(GENTIME_COLUMN, request.getStop());
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
    public void exportPacket(Context ctx, ExportPacketRequest request, Observer<HttpBody> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        long gentime = TimeEncoding.fromProtobufTimestamp(request.getGentime());
        int seqNum = request.getSeqnum();

        var sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);
        if (request.hasPname()) { // Optional due to deprecated API where name is not provided
            sqlb = sqlb.where("pname = ?", request.getPname());
        }
        sqlb = sqlb.where("gentime = ?", gentime);
        sqlb = sqlb.where("seqNum = ?", seqNum);

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
                    String timestamp = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()
                            .truncatedTo(ChronoUnit.MILLIS))
                            .replace("-", "")
                            .replace(":", "")
                            .replace(".", "");
                    observer.complete(HttpBody.newBuilder()
                            .setFilename("packet-" + timestamp + "-" + seqNum + ".raw")
                            .setContentType(MediaType.OCTET_STREAM.toString())
                            .setData(packets.get(0).getPacket())
                            .build());
                }
            }
        });
    }

    @Override
    public void extractPacket(Context ctx, ExtractPacketRequest request, Observer<ExtractPacketResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        long gentime = TimeEncoding.fromProtobufTimestamp(request.getGentime());
        int seqNum = request.getSeqnum();

        var sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);
        if (request.hasPname()) { // Optional due to deprecated API where name is not provided
            sqlb = sqlb.where("pname = ?", request.getPname());
        }
        sqlb = sqlb.where("gentime = ?", gentime);
        sqlb = sqlb.where("seqNum = ?", seqNum);

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
                    var packet = packets.get(0);

                    var mdb = MdbFactory.getInstance(instance);

                    // Best effort to find a suitable root container
                    // It could be that the MDB has changed so much that this
                    // logic doesn't work, so then we fallback to using the default.
                    var candidate = mdb.getSequenceContainer(packet.getId());
                    SequenceContainer rootContainer = candidate;
                    while (candidate != null) {
                        rootContainer = candidate;
                        candidate = candidate.getBaseContainer();
                    }
                    if (rootContainer != null) {
                        mdb.setRootSequenceContainer(rootContainer);
                    }

                    var responseb = ExtractPacketResponse.newBuilder();

                    var pdata = new ProcessorData(instance, "XTCEPROC", mdb, new ProcessorConfig());

                    var eventProducer = new AbstractEventProducer() {
                        @Override
                        public void sendEvent(Event event) {
                            responseb.addMessages(event.getMessage());
                        }

                        @Override
                        public void close() {
                            // NOP
                        }

                        @Override
                        public long getMissionTime() {
                            return TimeEncoding.INVALID_INSTANT;
                        }
                    };
                    eventProducer.setSource("Extraction");
                    pdata.setEventProducer(eventProducer);

                    var extractor = new XtceTmExtractor(mdb, pdata);
                    extractor.getOptions().setIgnoreOutOfContainerEntries(true);
                    extractor.provideAll();

                    var bytes = packet.getPacket().toByteArray();
                    var result = extractor.processPacket(bytes, gentime, gentime, seqNum);
                    var packetName = XtceTmRecorder.deriveArchivePartition(result);
                    responseb.setPacketName(packetName);

                    for (var pval : result.getParameterResult()) {
                        if (pval instanceof ContainerParameterValue) {
                            var containedPval = (ContainerParameterValue) pval;
                            var container = containedPval.getSequenceEntry().getSequenceContainer();
                            var pvalb = ExtractedParameterValue.newBuilder()
                                    .setParameter(XtceToGpbAssembler.toParameterInfo(
                                            containedPval.getParameter(), DetailLevel.SUMMARY))
                                    .setEntryContainer(XtceToGpbAssembler.toContainerInfo(container, DetailLevel.LINK))
                                    .setLocation(containedPval.getAbsoluteBitOffset())
                                    .setSize(containedPval.getBitSize());
                            if (containedPval.getRawValue() != null) {
                                pvalb.setRawValue(ValueUtility.toGbp(containedPval.getRawValue()));
                            }
                            if (containedPval.getEngValue() != null) {
                                pvalb.setEngValue(ValueUtility.toGbp(containedPval.getEngValue()));
                            }
                            responseb.addParameterValues(pvalb);
                        }
                    }

                    observer.complete(responseb.build());
                }
            }
        });
    }

    @Override
    public void exportPackets(Context ctx, ExportPacketsRequest request, Observer<HttpBody> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());

        Set<String> nameSet = new HashSet<>(request.getNameList());
        ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadPacket, nameSet);

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual(GENTIME_COLUMN, request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore(GENTIME_COLUMN, request.getStop());
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

    @Override
    public void subscribePackets(Context ctx, SubscribePacketsRequest request, Observer<TmPacketData> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());

        if (request.hasProcessor()) {
            Mdb mdb = MdbFactory.getInstance(instance);
            Processor processor = ProcessingApi.verifyProcessor(instance, request.getProcessor());
            ContainerRequestManager containerRequestManager = processor.getContainerRequestManager();
            ContainerConsumer containerConsumer = result -> {
                TmPacketData packet = TmPacketData.newBuilder()
                        .setId(NamedObjectId.newBuilder().setName(result.getContainer().getQualifiedName()))
                        .setPacket(ByteString.copyFrom(result.getContainerContent()))
                        .setSize(result.getContainerContent().length)
                        .setGenerationTime(TimeEncoding.toProtobufTimestamp(result.getGenerationTime()))
                        .setReceptionTime(TimeEncoding.toProtobufTimestamp(result.getAcquisitionTime()))
                        .build();
                observer.next(packet);
            };
            observer.setCancelHandler(
                    () -> containerRequestManager.unsubscribe(containerConsumer, mdb.getRootSequenceContainer()));
            containerRequestManager.subscribe(containerConsumer, mdb.getRootSequenceContainer());

        } else if (request.hasStream()) {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
            Stream stream = TableApi.verifyStream(ctx, ydb, request.getStream());
            StreamSubscriber streamSubscriber = new StreamSubscriber() {
                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    byte[] pktData = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
                    long genTime = (Long) tuple.getColumn(GENTIME_COLUMN);
                    long receptionTime = (Long) tuple.getColumn(StandardTupleDefinitions.TM_RECTIME_COLUMN);
                    int seqNumber = (Integer) tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN);
                    TmPacketData tm = TmPacketData.newBuilder().setPacket(ByteString.copyFrom(pktData))
                            .setSize(pktData.length)
                            .setGenerationTime(TimeEncoding.toProtobufTimestamp(genTime))
                            .setReceptionTime(TimeEncoding.toProtobufTimestamp(receptionTime))
                            .setSequenceNumber(seqNumber)
                            .build();
                    observer.next(tm);
                }

                @Override
                public void streamClosed(Stream stream) {
                    observer.complete();
                }
            };
            observer.setCancelHandler(() -> stream.removeSubscriber(streamSubscriber));
            stream.addSubscriber(streamSubscriber);
        } else {
            throw new BadRequestException("One of 'processor' or 'stream' must be set");
        }
    }

    @Override
    public void subscribeContainers(Context ctx, SubscribeContainersRequest request, Observer<ContainerData> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        Mdb mdb = MdbFactory.getInstance(instance);
        if (request.getNamesCount() == 0) {
            throw new BadRequestException("At least one container name must be specified");
        }
        ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadPacket, request.getNamesList());

        List<SequenceContainer> containers = new ArrayList<>(request.getNamesCount());
        for (String name : request.getNamesList()) {
            SequenceContainer container = mdb.getSequenceContainer(name);
            if (container == null) {
                throw new BadRequestException("Unknown container '" + name + "'");
            }
            containers.add(container);
        }

        Processor processor = ProcessingApi.verifyProcessor(instance, request.getProcessor());
        ContainerRequestManager containerRequestManager = processor.getContainerRequestManager();
        ContainerConsumer containerConsumer = result -> {
            ContainerData packet = ContainerData.newBuilder()
                    .setName(result.getContainer().getQualifiedName())
                    .setBinary(ByteString.copyFrom(result.getContainerContent()))
                    .setGenerationTime(TimeEncoding.toProtobufTimestamp(result.getGenerationTime()))
                    .setReceptionTime(TimeEncoding.toProtobufTimestamp(result.getAcquisitionTime()))
                    .setSeqCount(result.getSeqCount())
                    .build();
            observer.next(packet);
        };
        observer.setCancelHandler(() -> {
            for (SequenceContainer container : containers) {
                containerRequestManager.unsubscribe(containerConsumer, container);
            }
        });
        for (SequenceContainer container : containers) {
            containerRequestManager.subscribe(containerConsumer, container);
        }
    }

    /**
     * Get packet names this user has appropriate privileges for.
     */
    private Set<String> getTmPacketNames(String yamcsInstance, User user) {
        var result = new HashSet<String>();

        var ydb = YarchDatabase.getInstance(yamcsInstance);
        var tableDefinition = ydb.getTable(XtceTmRecorder.TABLE_NAME);
        if (tableDefinition != null) {
            BiMap<String, Short> enumValues = tableDefinition.getEnumValues(XtceTmRecorder.PNAME_COLUMN);
            if (enumValues != null) {
                for (var pname : enumValues.keySet()) {
                    if (user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, pname)) {
                        result.add(pname);
                    }
                }
            }
        }
        return result;
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
