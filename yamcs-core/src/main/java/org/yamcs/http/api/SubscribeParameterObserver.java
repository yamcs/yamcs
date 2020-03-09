package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.security.User;
import org.yamcs.utils.StringConverter;

public class SubscribeParameterObserver implements Observer<SubscribeParametersRequest> {

    private static final Log log = new Log(SubscribeParameterObserver.class);

    private User user;
    private Observer<SubscribeParametersData> responseObserver;

    private int subscriptionId = -1;
    private ParameterWithIdRequestHelper pidrm;

    private Map<NamedObjectId, Integer> numericIdMap = new HashMap<>();
    private AtomicInteger numericIdGenerator = new AtomicInteger();

    public SubscribeParameterObserver(User user, Observer<SubscribeParametersData> responseObserver) {
        this.user = user;
        this.responseObserver = responseObserver;
    }

    @Override
    public void next(SubscribeParametersRequest request) {
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
                        datab.addValues(pval.toGpb(numericId));
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
                        log.warn("Received subscribe attempt will all-invalid parameters");
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

            for (NamedObjectId id : idList) {
                int numericId = numericIdGenerator.incrementAndGet();
                numericIdMap.put(id, numericId);
                datab.putMapping(numericId, id);
            }
            if (!request.hasSendFromCache() || request.getSendFromCache()) {
                for (ParameterValueWithId rec : pidrm.getValuesFromCache(subscriptionId)) {
                    ParameterValue pval = rec.getParameterValue();
                    int numericId = numericIdMap.get(rec.getId());
                    datab.addValues(pval.toGpb(numericId));
                }
            }

            responseObserver.next(datab.build());
        } catch (InvalidIdentification e) {
            log.warn("Invalid identification: {}", e.getMessage());
            responseObserver.completeExceptionally(e);
        } catch (NoPermissionException e) {
            log.warn("No permission for parameters: {}", e.getMessage());
            responseObserver.completeExceptionally(e);
        }
    }

    private void updateSubscription(Action action, List<NamedObjectId> idList, boolean updateOnExpiration)
            throws NoPermissionException, InvalidIdentification {
        if (action == Action.REPLACE) {
            if (subscriptionId != -1) {
                pidrm.removeRequest(subscriptionId);
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
