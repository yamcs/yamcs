package org.yamcs.http.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.TmReceptionTimeIndex;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ServiceUnavailableException;
import org.yamcs.protobuf.ListPacketsRequest;
import org.yamcs.protobuf.ListPacketsResponse;
import org.yamcs.protobuf.PacketTimeBasis;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DbRange;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableVisitor;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.SecondaryIndexKey;
import org.yamcs.yarch.rocksdb.SecondaryIndexTableWalker;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.SecondaryIndex.State;

import com.google.gson.Gson;

/** Shared reception-time reader for packet pages and raw exports. */
public final class TmPacketArchiveQuery implements AutoCloseable {
    private final TableDefinition definition;
    private final SecondaryIndexTableWalker walker;
    private final Set<String> names;
    private final String link;
    private final PacketFilter filter;
    private final long revision;

    public TmPacketArchiveQuery(YarchDatabaseInstance database, Long start, Long stop, boolean ascending,
            Set<String> authorizedNames, String link, String filter) {
        var index = TmReceptionTimeIndex.get(database);
        if (index == null || index.getInfo().getState() != State.READY || !index.getInfo().getManaged()) {
            throw new ServiceUnavailableException("Reception-time index is "
                    + (index == null ? "ABSENT" : index.getInfo().getState()));
        }
        definition = database.getTable(XtceTmRecorder.TABLE_NAME);
        revision = index.getInfo().getBuildRevision();
        names = Set.copyOf(authorizedNames);
        this.link = link;
        this.filter = filter == null ? null : PacketFilterFactory.create(filter);
        walker = (SecondaryIndexTableWalker) database.getStorageEngine(definition)
                .newSecondaryIndexTableWalker(database, definition, ascending, false);
        DbRange range = new DbRange();
        range.rangeStart = start == null ? null : SecondaryIndexKey.prefix(definition, start);
        range.rangeEnd = stop == null ? null : SecondaryIndexKey.prefix(definition, stop);
        walker.setSecondaryIndexRange(range);
        walker.setStopBefore(range.rangeEnd);
        walker.setExpectedBuildRevision(revision);
        Set<Object> enumNames = new HashSet<>();
        var enums = definition.getEnumValues("pname");
        if (enums != null) {
            for (String name : names) {
                if (enums.containsKey(name)) {
                    enumNames.add(enums.get(name));
                }
            }
        }
        walker.setPartitionFilter(null, enumNames);
    }

    public void walk(Predicate<Tuple> receiver, BooleanSupplier cancelled) throws Exception {
        try {
            walker.walk((key, value) -> {
                if (cancelled.getAsBoolean()) {
                    return TableVisitor.ACTION_STOP;
                }
                Tuple tuple = definition.deserialize(key, value);
                if (!names.contains(tuple.getColumn("pname"))
                        || (link != null && !link.equals(tuple.getColumn("link")))
                        || (filter != null && !filter.matches(tuple))) {
                    return TableVisitor.ACTION_CONTINUE;
                }
                return receiver.test(tuple) ? TableVisitor.ACTION_CONTINUE : TableVisitor.ACTION_STOP;
            });
        } catch (YarchException e) {
            throw new ServiceUnavailableException("Reception-time archive query unavailable", e);
        }
    }

    public ListPacketsResponse list(ListPacketsRequest request, BooleanSupplier cancelled) throws Exception {
        int limit = request.hasLimit() ? request.getLimit() : 100;
        if (limit <= 0 || (request.hasPos() && request.getPos() < 0)) {
            throw new BadRequestException("limit must be positive and pos must be nonnegative");
        }
        boolean ascending = "asc".equals(request.getOrder());
        String fingerprint = fingerprint(request);
        if (request.hasNext()) {
            var token = PageToken.decode(request.getNext());
            if (token.revision != revision || token.ascending != ascending || !fingerprint.equals(token.fingerprint)) {
                throw new BadRequestException("Continuation token does not match this query or index revision");
            }
            byte[] cursor;
            try {
                cursor = Base64.getUrlDecoder().decode(token.key);
                if (cursor.length != 24
                        || cursor[0] != (byte) (0x70 | definition.getColumnDefinition("rectime").getType().getTypeId())
                        || cursor[9] != (byte) (0x70 | definition.getColumnDefinition("pname").getType().getTypeId())) {
                    throw new IllegalArgumentException();
                }
            } catch (RuntimeException e) {
                throw new BadRequestException("Invalid reception-time continuation key");
            }
            walker.setResumeAfter(cursor);
        }
        long offset = !request.hasNext() && request.hasPos() ? request.getPos() : 0;
        long[] skipped = { 0 };
        byte[][] lastKey = { null };
        boolean[] more = { false };
        var response = ListPacketsResponse.newBuilder();
        walk(tuple -> {
            if (skipped[0]++ < offset) {
                return true;
            }
            if (response.getPacketsCount() == limit) {
                more[0] = true;
                return false;
            }
            var packet = GPBHelper.tupleToTmPacketData(tuple);
            response.addPackets(packet).addPacket(packet);
            lastKey[0] = walker.getLastKey();
            return true;
        }, cancelled);
        if (more[0]) {
            PageToken token = new PageToken();
            token.revision = revision;
            token.ascending = ascending;
            token.fingerprint = fingerprint;
            token.key = Base64.getUrlEncoder().withoutPadding().encodeToString(lastKey[0]);
            response.setContinuationToken(token.encode());
        }
        return response.build();
    }

    static String fingerprint(ListPacketsRequest request) {
        var canonical = request.toBuilder().clearNext().clearLimit().clearPos()
                .setTimeBasis(PacketTimeBasis.RECEPTION_TIME)
                .setOrder("asc".equals(request.getOrder()) ? "asc" : "desc").clearName()
                .addAllName(request.getNameList().stream().distinct().sorted().toList());
        if (request.getLink().isEmpty()) {
            canonical.clearLink();
        }
        if (request.getFilter().isEmpty()) {
            canonical.clearFilter();
        }
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(canonical.build().toByteArray()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        walker.close();
    }

    static final class PageToken {
        int version = 1;
        String timeBasis = "RECEPTION_TIME";
        long revision;
        boolean ascending;
        String fingerprint;
        String key;

        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(new Gson().toJson(this).getBytes(StandardCharsets.UTF_8));
        }

        static PageToken decode(String encoded) {
            try {
                if (encoded.length() > 4096) {
                    throw new IllegalArgumentException();
                }
                PageToken token = new Gson().fromJson(new String(Base64.getUrlDecoder().decode(encoded),
                        StandardCharsets.UTF_8), PageToken.class);
                if (token == null || token.version != 1 || !"RECEPTION_TIME".equals(token.timeBasis)
                        || token.key == null || token.fingerprint == null) {
                    throw new IllegalArgumentException();
                }
                return token;
            } catch (RuntimeException e) {
                throw new BadRequestException("Invalid reception-time continuation token");
            }
        }
    }
}
