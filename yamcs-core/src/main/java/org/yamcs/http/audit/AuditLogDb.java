package org.yamcs.http.audit;

import static org.yamcs.api.AnnotationsProto.fieldBehavior;

import java.util.Base64;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.InitException;
import org.yamcs.api.FieldBehavior;
import org.yamcs.logging.Log;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.ResultListener;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

public class AuditLogDb {

    private static final Random RG = new Random();

    /*
     * Differentiates between multiple records with the same timestamp
     */
    private AtomicInteger seqNumSequence = new AtomicInteger();

    private static final String TABLE_NAME = "audit_log";
    private static final TupleDefinition TDEF = new TupleDefinition();
    public static final String CNAME_RECTIME = "rectime";
    public static final String CNAME_SEQNUM = "seqNum";
    public static final String CNAME_USER = "user";
    public static final String CNAME_SERVICE = "service";
    public static final String CNAME_METHOD = "method";
    public static final String CNAME_SUMMARY = "summary";
    public static final String CNAME_REQUEST = "request";
    private static final DataType STRUCT_TYPE = DataType.protobuf(Struct.class.getName());
    static {
        TDEF.addColumn(CNAME_RECTIME, DataType.TIMESTAMP);
        TDEF.addColumn(CNAME_SEQNUM, DataType.INT);
        TDEF.addColumn(CNAME_USER, DataType.STRING);
        TDEF.addColumn(CNAME_SERVICE, DataType.STRING);
        TDEF.addColumn(CNAME_METHOD, DataType.STRING);
        TDEF.addColumn(CNAME_SUMMARY, DataType.STRING);
        TDEF.addColumn(CNAME_REQUEST, STRUCT_TYPE);
    }

    private Log log;
    private YarchDatabaseInstance ydb;
    private Stream tableStream;
    private ReadWriteLock rwlock = new ReentrantReadWriteLock();

    public AuditLogDb(String yamcsInstance) throws InitException {
        log = new Log(AuditLog.class, yamcsInstance);
        ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            String streamName = TABLE_NAME + "_in";
            if (ydb.getTable(TABLE_NAME) == null) {
                String query = "create table " + TABLE_NAME + "(" + TDEF.getStringDefinition1()
                        + ", primary key(rectime, seqNum))";
                ydb.execute(query);
            }
            if (ydb.getStream(streamName) == null) {
                ydb.execute("create stream " + streamName + TDEF.getStringDefinition());
            }
            ydb.execute("upsert into " + TABLE_NAME + " select * from " + streamName);
            tableStream = ydb.getStream(streamName);
        } catch (StreamSqlException | ParseException e) {
            throw new InitException(e);
        }
    }

    public void addRecord(MethodDescriptor method, Message message, User user, String summary) {
        log.info("{}.{}: {}", method.getService().getName(), method.getName(), summary);
        rwlock.writeLock().lock();
        try {
            Tuple tuple = new Tuple();
            tuple.addColumn(CNAME_RECTIME, TimeEncoding.getWallclockTime());
            tuple.addColumn(CNAME_SEQNUM, seqNumSequence.getAndIncrement());
            tuple.addColumn(CNAME_SERVICE, method.getService().getName());
            tuple.addColumn(CNAME_METHOD, method.getName());
            tuple.addColumn(CNAME_USER, user.getName());
            tuple.addColumn(CNAME_SUMMARY, summary);
            tuple.addColumn(CNAME_REQUEST, STRUCT_TYPE, toStruct(message));
            tableStream.emitTuple(tuple);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void listRecords(int limit, String token, AuditRecordFilter filter, AuditRecordListener consumer) {
        rwlock.readLock().lock();
        try {
            SqlBuilder sqlBuilder = new SqlBuilder(TABLE_NAME);
            sqlBuilder.select("*");

            TimeInterval interval = filter.getTimeInterval();
            if (interval.hasEnd()) {
                sqlBuilder.where("rectime < ?", interval.getEnd());
            }
            if (interval.hasStart()) {
                sqlBuilder.where("rectime > ?", interval.getStart());
            }
            if (!filter.getServices().isEmpty()) {
                sqlBuilder.whereColIn("service", filter.getServices());
            }
            if (filter.getSearch() != null) {
                sqlBuilder.where("summary like ?", "%" + filter.getSearch() + "%");
            }
            sqlBuilder.descend(true);
            sqlBuilder.limit(limit + 1);

            StreamSqlStatement stmt = ydb.createStatement(sqlBuilder.toString(),
                    sqlBuilder.getQueryArguments().toArray());
            ydb.execute(stmt, new ResultListener() {
                int count = 0;

                @Override
                public void next(Tuple tuple) {
                    if (count < limit) {
                        consumer.next(new AuditRecord(tuple));
                    }
                    count++;
                }

                @Override
                public void completeExceptionally(Throwable t) {
                    consumer.completeExceptionally(t);
                }

                @Override
                public void complete() {
                    if (count == limit) {
                        consumer.complete(getRandomToken());
                    } else {
                        consumer.complete(null);
                    }
                }
            });
        } catch (StreamSqlException | ParseException e) {
            throw new RuntimeException(e);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    // Convert the message to a form that can be read forever
    private static Struct toStruct(Message message) {
        Struct.Builder structb = Struct.newBuilder();
        for (Entry<FieldDescriptor, Object> entry : message.getAllFields().entrySet()) {
            FieldDescriptor field = entry.getKey();
            FieldOptions fieldOptions = field.getOptions();
            if (fieldOptions.getExtension(fieldBehavior).contains(FieldBehavior.SECRET)) {
                structb.putFields(field.getName(), toValue("***"));
            } else {
                structb.putFields(field.getName(), toValue(entry.getValue()));
            }
        }
        return structb.build();
    }

    private static ListValue toListValue(List<?> value) {
        ListValue.Builder listb = ListValue.newBuilder();
        for (Object el : value) {
            listb.addValues(toValue(el));
        }
        return listb.build();
    }

    private static Value toValue(Object value) {
        if (value == null) {
            return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        } else if (value instanceof Boolean) {
            return Value.newBuilder().setBoolValue((Boolean) value).build();
        } else if (value instanceof Float) {
            return Value.newBuilder().setNumberValue((Float) value).build();
        } else if (value instanceof Double) {
            return Value.newBuilder().setNumberValue((Double) value).build();
        } else if (value instanceof Byte) {
            return Value.newBuilder().setNumberValue((Byte) value).build();
        } else if (value instanceof Short) {
            return Value.newBuilder().setNumberValue((Short) value).build();
        } else if (value instanceof Integer) {
            return Value.newBuilder().setNumberValue((Integer) value).build();
        } else if (value instanceof List) {
            return Value.newBuilder().setListValue(toListValue((List<?>) value)).build();
        } else if (value instanceof Message) {
            return Value.newBuilder().setStructValue(toStruct((Message) value)).build();
        } else { // Especially "Long", which we don't want to put in a double
            return Value.newBuilder().setStringValue(value.toString()).build();
        }
    }

    private static String getRandomToken() {
        byte[] b = new byte[16];
        RG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
