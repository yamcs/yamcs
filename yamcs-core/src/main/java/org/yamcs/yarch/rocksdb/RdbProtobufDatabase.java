package org.yamcs.yarch.rocksdb;

import static org.yamcs.utils.ByteArrayUtils.encodeInt;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.rocksdb.RocksDBException;
import org.yamcs.logging.Log;
import org.yamcs.yarch.ProtobufDatabase;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

import com.google.protobuf.Message;

public class RdbProtobufDatabase implements ProtobufDatabase {

    private Log log;

    private Tablespace tablespace;
    private int tbsIndex;

    RdbProtobufDatabase(String yamcsInstance, Tablespace tablespace) throws RocksDBException {
        log = new Log(getClass(), yamcsInstance);
        this.tablespace = tablespace;

        List<TablespaceRecord> tbsRecords = tablespace.filter(Type.PROTOBUF, yamcsInstance, trb -> true);
        if (tbsRecords.isEmpty()) {
            TablespaceRecord.Builder b = TablespaceRecord.newBuilder().setType(Type.PROTOBUF);
            TablespaceRecord tbsRec = tablespace.createMetadataRecord(yamcsInstance, b);
            tbsIndex = tbsRec.getTbsIndex();
            log.info("Created new protobuf db tbsIndex: {}", tbsIndex);
        } else {
            tbsIndex = tbsRecords.get(0).getTbsIndex();
        }
    }

    @Override
    public void save(String id, Message message) throws IOException {
        try {
            tablespace.putData(getKey(id), message.toByteArray());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    @Override
    public <T extends Message> T get(String id, Class<T> messageClass) throws IOException {
        try {
            byte[] key = getKey(id);
            byte[] value = tablespace.getData(key);
            return value != null ? getMessage(value, messageClass) : null;
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean delete(String id) throws IOException {
        try {
            byte[] key = getKey(id);
            byte[] value = tablespace.getData(key);
            if (value == null) {
                return false;
            } else {
                tablespace.remove(key);
                return true;
            }
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    private byte[] getKey(String id) {
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[TBS_INDEX_SIZE + idBytes.length];
        encodeInt(tbsIndex, key, 0);
        System.arraycopy(idBytes, 0, key, TBS_INDEX_SIZE, idBytes.length);
        return key;
    }

    @SuppressWarnings("unchecked")
    private <T extends Message> T getMessage(byte[] bytes, Class<T> messageClass) throws IOException {
        // Yes reflection is "slow", but we really don't expect this getting called frequently.
        try {
            Method m = messageClass.getMethod("parseFrom", new Class[] { byte[].class });
            return (T) m.invoke(null, new Object[] { bytes });
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
