package org.yamcs.memento;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.YamcsServerInstance;
import org.yamcs.management.ManagementListener;
import org.yamcs.management.ManagementService;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * General purpose facility for persisting state information.
 */
public class MementoDb implements ManagementListener {

    private static final String TABLE_NAME = "memento";
    private static final TupleDefinition TDEF = new TupleDefinition();
    private static final String CNAME_KEY = "key";
    private static final String CNAME_VALUE = "value";
    private static ConcurrentMap<String, MementoDb> dbs = new ConcurrentHashMap<>();
    static {
        TDEF.addColumn(CNAME_KEY, DataType.STRING);

        // the 'value' column is a string type, but we really treat it as if it was a json type
        // (which yarch does not natively support), so it can represent primitives, objects or arrays.
        TDEF.addColumn(CNAME_VALUE, DataType.STRING);

        // TODO Figure out a better way to get rid of outdated DBs.
        // (we don't have a service available here).
        ManagementService.getInstance().addManagementListener(new ManagementListener() {
            @Override
            public void instanceStateChanged(YamcsServerInstance ysi) {
                switch (ysi.state()) {
                case OFFLINE:
                case FAILED:
                    dbs.remove(ysi.getName());
                    break;
                default:
                    // Ignore
                }
            }
        });
    }

    private YarchDatabaseInstance ydb;
    private Stream tableStream;
    private ReadWriteLock rwlock = new ReentrantReadWriteLock();

    private MementoDb(String yamcsInstance) throws StreamSqlException, ParseException {
        ydb = YarchDatabase.getInstance(yamcsInstance);

        var streamName = "memento_in";
        if (ydb.getTable(TABLE_NAME) == null) {
            var query = "create table memento(" + TDEF.getStringDefinition1() + ", primary key(\"key\"))";
            ydb.execute(query);
        }
        if (ydb.getStream(streamName) == null) {
            ydb.execute("create stream " + streamName + TDEF.getStringDefinition());
        }
        ydb.execute("upsert into memento select * from " + streamName);
        tableStream = ydb.getStream(streamName);
    }

    /**
     * Retrieve a {@link MementoDb} for the given Yamcs instance.
     */
    public static MementoDb getInstance(String yamcsInstance) {
        return dbs.computeIfAbsent(yamcsInstance, x -> {
            try {
                return new MementoDb(yamcsInstance);
            } catch (StreamSqlException | ParseException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Returns the boolean value of the given key.
     * <p>
     * An empty optional is returned if the key is missing, or the value cannot be read as a boolean.
     */
    public Optional<Boolean> getBoolean(String key) {
        var value = getJsonValue(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return Optional.of(value.getAsBoolean());
        }
        return Optional.empty();
    }

    /**
     * Sets the value of the given key to the given boolean value.
     */
    public void putBoolean(String key, Boolean value) {
        putJsonValue(key, value != null ? new JsonPrimitive(value) : null);
    }

    /**
     * Returns the number value of the given key.
     * <p>
     * An empty optional is returned if the key is missing, or the value cannot be read as a number.
     */
    public Optional<Number> getNumber(String key) {
        var value = getJsonValue(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return Optional.of(value.getAsNumber());
        }
        return Optional.empty();
    }

    /**
     * Sets the value of the given key to the given number value.
     */
    public void putNumber(String key, Number value) {
        putJsonValue(key, value != null ? new JsonPrimitive(value) : null);
    }

    /**
     * Returns the string value of the given key.
     * <p>
     * An empty optional is returned if the key is missing, or the value cannot be read as a string.
     */
    public Optional<String> getString(String key) {
        var value = getJsonValue(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return Optional.of(value.getAsString());
        }
        return Optional.empty();
    }

    /**
     * Sets the value of the given key to the given string value.
     */
    public void putString(String key, String value) {
        putJsonValue(key, value != null ? new JsonPrimitive(value) : null);
    }

    /**
     * Returns the JsonObject value of the given key.
     * <p>
     * An empty optional is returned if the key is missing, or the value cannot be read as a JsonObject.
     */
    public Optional<JsonObject> getJsonObject(String key) {
        var value = getJsonValue(key);
        if (value != null && value.isJsonObject()) {
            return Optional.of(value.getAsJsonObject());
        }
        return Optional.empty();
    }

    /**
     * Sets the value of the given key to the given JsonObject value.
     */
    public void putJsonObject(String key, JsonObject value) {
        putJsonValue(key, value);
    }

    /**
     * Returns the JsonArray value of the given key.
     * <p>
     * An empty optional is returned if the key is missing, or the value cannot be read as a JsonArray.
     */
    public Optional<JsonArray> getJsonArray(String key) {
        var value = getJsonValue(key);
        if (value != null && value.isJsonArray()) {
            return Optional.of(value.getAsJsonArray());
        }
        return Optional.empty();

    }

    /**
     * Sets the value of the given key to the given JsonArray value.
     */
    public void putJsonArray(String key, JsonArray value) {
        putJsonValue(key, value);
    }

    /**
     * Retrieves an object saved as memento. The object is deserialized using GSON.
     */
    public <T> Optional<T> getObject(String key, Class<T> clazz) {
        var jsonObject = getJsonObject(key);
        if (jsonObject.isPresent()) {
            var obj = new Gson().fromJson(jsonObject.get(), clazz);
            return Optional.of(obj);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Sets the value of the given key by serializing the provided object using GSON.
     */
    public <T> void putObject(String key, T object) {
        var jsonObject = new Gson().toJsonTree(object).getAsJsonObject();
        putJsonObject(key, jsonObject);
    }

    private JsonElement getJsonValue(String key) {
        rwlock.readLock().lock();
        try {
            var queryResult = ydb.executeUnchecked("select * from memento where \"key\" = ?", key);
            try {
                if (queryResult.hasNext()) {
                    var tuple = queryResult.next();
                    var stringValue = tuple.<String> getColumn(CNAME_VALUE);
                    if (stringValue != null) {
                        return new Gson().fromJson(stringValue, JsonElement.class);
                    }
                }
                return null;
            } finally {
                queryResult.close();
            }
        } finally {
            rwlock.readLock().unlock();
        }
    }

    private void putJsonValue(String key, JsonElement value) {
        Objects.requireNonNull(key, "key must not be null");
        rwlock.writeLock().lock();
        try {
            if (value == null) {
                var sqlResult = ydb.executeUnchecked("delete from memento where \"key\" = ?", key);
                sqlResult.close();
            } else {
                var tuple = new Tuple();
                tuple.addColumn(CNAME_KEY, key);
                tuple.addColumn(CNAME_VALUE, value.toString());
                tableStream.emitTuple(tuple);
            }
        } finally {
            rwlock.writeLock().unlock();
        }
    }
}
