package org.yamcs.http.api;

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
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.SubscribeParametersData;
import org.yamcs.protobuf.SubscribeParametersRequest;
import org.yamcs.protobuf.SubscribeParametersRequest.Action;
import org.yamcs.protobuf.SubscribedParameterInfo;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.security.User;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.DataType;
import org.yamcs.xtce.util.DataTypeUtil;

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
                SubscribeParametersData.Builder datab = SubscribeParametersData.newBuilder();
                for (ParameterValueWithId pvwi : params) {
                    ParameterValue pval = pvwi.getParameterValue();
                    Integer numericId = numericIdMap.get(pvwi.getId());
                    if (numericId != null) {
                        datab.addValues(toGpb(pval, numericId));
                    }
                }
                responseObserver.next(datab.build());
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

            SubscribeParametersData.Builder datab = SubscribeParametersData.newBuilder()
                    .addAllInvalid(invalid);

            Map<NamedObjectId, Integer> mappingUpdate = new HashMap<>(idList.size());
            for (NamedObjectId id : idList) {
                int numericId = numericIdGenerator.incrementAndGet();
                mappingUpdate.put(id, numericId);
                datab.putMapping(numericId, id);

                var info = generateInfo(id);
                datab.putInfo(numericId, info);
            }
            if (subscriptionId != -1 && (!request.hasSendFromCache() || request.getSendFromCache())) {
                for (ParameterValueWithId rec : pidrm.getValuesFromCache(subscriptionId)) {
                    ParameterValue pval = rec.getParameterValue();
                    Integer numericId = mappingUpdate.get(rec.getId());
                    if (numericId != null) {
                        datab.addValues(toGpb(pval, numericId));
                    }
                }
            }

            responseObserver.next(datab.build());

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
        }
    }

    private SubscribedParameterInfo generateInfo(NamedObjectId id) {
        var infob = SubscribedParameterInfo.newBuilder();
        try {
            var parameterWithId = ParameterWithIdRequestHelper.checkName(pidrm.getPrm(), id);
            var parameter = parameterWithId.getParameter();

            infob.setParameter(parameter.getQualifiedName());

            if (parameter.getParameterType() != null) {
                DataType dtype = parameter.getParameterType();
                if (parameterWithId.getPath() != null) {
                    dtype = DataTypeUtil.getMemberType(dtype, parameterWithId.getPath());
                }

                if (dtype instanceof BaseDataType) {
                    var unitSet = ((BaseDataType) dtype).getUnitSet();
                    if (!unitSet.isEmpty()) {
                        var units = unitSet.get(0).getUnit();
                        infob.setUnits(units);
                    }
                }
            }
        } catch (InvalidIdentification e) {
            // Ignore
        }
        return infob.build();
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
}
