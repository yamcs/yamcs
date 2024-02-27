package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.InvalidIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.Processor;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.YamcsEncoded;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.SubscribeParametersRequest.Action;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.security.User;
import org.yamcs.utils.StringConverter;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MapEntry;
import com.google.protobuf.WireFormat;

public class SubscribeParameterObserver implements Observer<SubscribeParametersRequest> {

    private static final Log log = new Log(SubscribeParameterObserver.class);

    private User user;
    private Observer<SubscribeParametersData> responseObserver;

    private int subscriptionId = -1;
    private ParameterWithIdRequestHelper pidrm;

    private ConcurrentMap<NamedObjectId, Integer> numericIdMap = new ConcurrentHashMap<>();
    private AtomicInteger numericIdGenerator = new AtomicInteger();

    // Max emitted bytes for a singular binary value updates (either raw or eng)
    private int maxBytes = -1;

    public SubscribeParameterObserver(User user, Observer<SubscribeParametersData> responseObserver) {
        this.user = user;
        this.responseObserver = responseObserver;
    }

    @Override
    public void next(SubscribeParametersRequest request) {
        if (request.hasMaxBytes()) {
            maxBytes = request.getMaxBytes();
        }

        if (pidrm == null) {
            Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
            ParameterRequestManager requestManager = processor.getParameterRequestManager();
            pidrm = new ParameterWithIdRequestHelper(requestManager, (subscriptionId, params) -> {
                if (params.isEmpty()) {
                    return;
                }
                SubscribeParametersData datab = new SubscribeParametersData();
                for (ParameterValueWithId pvwi : params) {
                    ParameterValue pval = pvwi.getParameterValue();
                    Integer numericId = numericIdMap.get(pvwi.getId());
                    if (numericId != null) {
                        datab.addValues(numericId, pval);
                    }
                }
                responseObserver.next(datab);
            });
        }

        Action action = Action.REPLACE;
        if (request.hasAction()) {
            action = request.getAction();
        }

        try {
            List<NamedObjectId> idList = request.getIdList();
            List<NamedObjectId> invalid = new ArrayList<>();
            try {
                updateSubscription(action, idList, request.getUpdateOnExpiration());
            } catch (InvalidIdentification e) {
                System.out.println("invalid: " + invalid);
                invalid.addAll(e.getInvalidParameters());

                if (!request.hasAbortOnInvalid() || request.getAbortOnInvalid()) {
                    BadRequestException ex = new BadRequestException(e);
                    ex.setDetail(NamedObjectList.newBuilder().addAllList(invalid).build());
                    responseObserver.completeExceptionally(ex);
                } else {
                    if (idList.size() == e.getInvalidParameters().size()) {
                        log.warn("Received subscribe attempt with only invalid parameters");
                        idList = Collections.emptyList();
                    } else {
                        Set<NamedObjectId> valid = new HashSet<>(idList);
                        valid.removeAll(e.getInvalidParameters());
                        idList = new ArrayList<>(valid);

                        log.warn("Received subscribe attempt with {} invalid parameters. "
                                + "Subscription will continue with {} remaining valids.",
                                e.getInvalidParameters().size(), idList.size());
                        if (log.isDebugEnabled()) {
                            log.debug("The invalid IDs are: {}",
                                    StringConverter.idListToString(e.getInvalidParameters()));
                        }
                        updateSubscription(action, idList, request.getUpdateOnExpiration());
                    }
                }
            }

            SubscribeParametersData datab = new SubscribeParametersData()
                    .addAllInvalid(invalid);

            Map<NamedObjectId, Integer> mappingUpdate = new HashMap<>(idList.size());
            for (NamedObjectId id : idList) {
                int numericId = numericIdGenerator.incrementAndGet();
                mappingUpdate.put(id, numericId);
                datab.putMapping(numericId, id);
            }
            if (subscriptionId != -1 && (!request.hasSendFromCache() || request.getSendFromCache())) {
                for (ParameterValueWithId rec : pidrm.getValuesFromCache(subscriptionId)) {
                    ParameterValue pval = rec.getParameterValue();
                    Integer numericId = mappingUpdate.get(rec.getId());
                    if (numericId != null) {
                        datab.addValues(numericId, pval);
                    }
                }
            }
            responseObserver.next(datab);

            // After having sent out the mapping, update internal state
            // (updates come from another thread, and we want to client to
            // know a mapping before receiving a value for it)
            numericIdMap.putAll(mappingUpdate);
        } catch (InvalidIdentification e) {
            log.warn("Invalid identification: {}", e.getMessage());
            responseObserver.completeExceptionally(e);
        } catch (NoPermissionException e) {
            log.warn("No permission for parameters: {}", e.getMessage());
            responseObserver.completeExceptionally(e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateSubscription(Action action, List<NamedObjectId> idList, boolean updateOnExpiration)
            throws NoPermissionException, InvalidIdentification {
        if (action == Action.REPLACE) {
            if (subscriptionId != -1) {
                pidrm.removeRequest(subscriptionId);
                subscriptionId = -1;
            }
            subscriptionId = pidrm.addRequest(idList, updateOnExpiration, user);
        } else if (action == Action.ADD) {
            if (subscriptionId == -1) {
                subscriptionId = pidrm.addRequest(idList, updateOnExpiration, user);
            } else {
                pidrm.addItemsToRequest(subscriptionId, idList, user);
            }
        } else if (action == Action.REMOVE) {
            if (subscriptionId != -1) {
                pidrm.removeItemsFromRequest(subscriptionId, idList, user);
            }
        }
    }

    private org.yamcs.protobuf.Pvalue.ParameterValue toGpb(ParameterValue pval, int numericId) {
        var gpb = pval.toGpb(numericId);
        if (maxBytes >= 0) {
            var hasRawBinaryValue = gpb.hasRawValue() && gpb.getRawValue().hasBinaryValue();
            var hasEngBinaryValue = gpb.hasEngValue() && gpb.getEngValue().hasBinaryValue();
            if (hasRawBinaryValue || hasEngBinaryValue) {
                var truncated = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder(gpb);
                if (hasRawBinaryValue) {
                    var binaryValue = gpb.getRawValue().getBinaryValue();
                    if (binaryValue.size() > maxBytes) {
                        truncated.getRawValueBuilder().setBinaryValue(
                                binaryValue.substring(0, maxBytes));
                    }
                }
                if (hasEngBinaryValue) {
                    var binaryValue = gpb.getEngValue().getBinaryValue();
                    if (binaryValue.size() > maxBytes) {
                        truncated.getEngValueBuilder().setBinaryValue(
                                binaryValue.substring(0, maxBytes));
                    }
                }
                return truncated.build();
            }
        }
        return gpb;
    }

    @Override
    public void completeExceptionally(Throwable t) {
        log.error("Parameter subscription errored", t);
        if (pidrm != null) {
            pidrm.quit();
        }
    }

    @Override
    public void complete() {
        if (pidrm != null) {
            pidrm.quit();
        }
    }

    static public class SubscribeParametersData {
        Map<Integer, ParameterValue> values = new HashMap<>();
        Map<Integer, NamedObjectId> mappings = new HashMap<>();
        List<NamedObjectId> invalid = new ArrayList<>();

        public void addValues(Integer numericId, ParameterValue pval) {
            values.put(numericId, pval);
        }

        public void putMapping(int numericId, NamedObjectId id) {
            mappings.put(numericId, id);
        }

        public SubscribeParametersData addAllInvalid(List<NamedObjectId> invalid) {
            this.invalid.addAll(invalid);
            return this;
        }

        int memoizedSize = -1;

        static final com.google.protobuf.MapEntry<java.lang.Integer, org.yamcs.protobuf.Yamcs.NamedObjectId> defaultEntry = com.google.protobuf.MapEntry.<java.lang.Integer, org.yamcs.protobuf.Yamcs.NamedObjectId> newDefaultInstance(
                null,
                com.google.protobuf.WireFormat.FieldType.UINT32,
                0,
                com.google.protobuf.WireFormat.FieldType.MESSAGE,
                org.yamcs.protobuf.Yamcs.NamedObjectId.getDefaultInstance());

        public int getSerializedSize() {
            int size = memoizedSize;
            if (size != -1)
                return size;

            size = 0;
            for (java.util.Map.Entry<Integer, NamedObjectId> entry : mappings
                    .entrySet()) {
                com.google.protobuf.MapEntry<Integer, NamedObjectId> mapping__ = defaultEntry
                        .newBuilderForType()
                        .setKey(entry.getKey())
                        .setValue(entry.getValue())
                        .build();
                size += com.google.protobuf.CodedOutputStream
                        .computeMessageSize(1, mapping__);
            }
            for (int i = 0; i < invalid.size(); i++) {
                size += com.google.protobuf.CodedOutputStream
                        .computeMessageSize(2, invalid.get(i));
            }
            for (Map.Entry<Integer, ParameterValue> entry : values.entrySet()) {
                size += CodedOutputStream.computeTagSize(3);
                size += YamcsEncoded
                        .computeLengthDelimitedFieldSize(entry.getValue().getSerializedSize(entry.getKey()));
            }

            memoizedSize = size;
            return size;
        }

        public void writeTo(CodedOutputStream output) throws IOException {
            serializeMapTo(output, mappings, defaultEntry, 1);

            for (int i = 0; i < invalid.size(); i++) {
                output.writeMessage(2, invalid.get(i));
            }
            for (Map.Entry<Integer, ParameterValue> entry : values.entrySet()) {
                int numericId = entry.getKey();
                ParameterValue pval = entry.getValue();
                output.writeTag(3, WireFormat.WIRETYPE_LENGTH_DELIMITED);
                output.writeUInt32NoTag(pval.getSerializedSize(numericId));
                pval.writeTo(output, numericId);
            }
        }

        @Override
        public String toString() {
            return "SubscribeParametersData [values=" + values + ", mappings=" + mappings + ", invalid=" + invalid
                    + ", memoizedSize=" + memoizedSize + "]";
        }

        private static <K, V> void serializeMapTo(
                CodedOutputStream out,
                Map<K, V> m,
                MapEntry<K, V> defaultEntry,
                int fieldNumber)
                throws IOException {
            for (Map.Entry<K, V> entry : m.entrySet()) {
                out.writeMessage(fieldNumber,
                        defaultEntry.newBuilderForType()
                                .setKey(entry.getKey())
                                .setValue(entry.getValue())
                                .build());
            }
        }
    }
}
