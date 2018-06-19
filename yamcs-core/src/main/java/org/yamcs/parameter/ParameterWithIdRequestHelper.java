package org.yamcs.parameter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.Parameter;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * This sits in front of the ParameterRequestManager and implements subscriptions based on NamedObjectId taking care to
 * send to the consumers the parameters with the requested id.
 * 
 * A client can request in fact the same parameter with two different names and they will get it twice each time.
 * 
 * In addition it can also provide updates on parameter expirations.
 * 
 * TODO: impose some subscription limits
 * 
 * @author nm
 *
 */
public class ParameterWithIdRequestHelper implements ParameterConsumer {
    ParameterRequestManager prm;
    final ParameterWithIdConsumer listener;
    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    Map<Integer, Subscription> subscriptions = new ConcurrentHashMap<>();

    // how often to check expiration
    private static long CHECK_EXPIRATION_INTERVAL = 1000;
    final static ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    public ParameterWithIdRequestHelper(ParameterRequestManager prm, ParameterWithIdConsumer listener) {
        this.prm = prm;
        this.listener = listener;
        schedulePeriodicExpirationChecking(this);
    }

    public int addRequest(List<NamedObjectId> idList, User user)
            throws InvalidIdentification, NoPermissionException {
        return addRequest(idList, false, user);
    }

    public int addRequest(List<NamedObjectId> idList, boolean checkExpiration, User user)
            throws InvalidIdentification, NoPermissionException {
        List<Parameter> plist = checkNames(idList);
        Subscription subscr = new Subscription(checkExpiration);
        for (int i = 0; i < idList.size(); i++) {
            checkParameterPrivilege(user, plist.get(i).getQualifiedName());
            subscr.put(plist.get(i), idList.get(i));
        }
        int subscriptionId = prm.addRequest(plist, this);
        subscriptions.put(subscriptionId, subscr);

        return subscriptionId;
    }

    public void addItemsToRequest(int subscriptionId, List<NamedObjectId> idList, User user)
            throws InvalidIdentification, NoPermissionException {
        Subscription subscr = subscriptions.get(subscriptionId);
        if (subscr == null) {
            log.warn("add item requested for an invalid subscription id {}", subscriptionId);
            throw new InvalidRequestIdentification("Invalid subcription id", subscriptionId);
        }
        List<Parameter> plist = checkNames(idList);
        synchronized (subscr) {
            for (int i = 0; i < idList.size(); i++) {
                Parameter p = plist.get(i);
                checkParameterPrivilege(user, p.getQualifiedName());
                NamedObjectId id = idList.get(i);
                if (!subscr.put(p, id)) {
                    log.info("Ignoring duplicate subscription for '{}', id: {}", p.getName(),
                            StringConverter.idToString(id));
                }
            }
        }
        prm.addItemsToRequest(subscriptionId, plist);
    }

    private static void schedulePeriodicExpirationChecking(ParameterWithIdRequestHelper x) {
        // trick to allow GC to collect this object and remove it from the timer
        final WeakReference<ParameterWithIdRequestHelper> ref = new WeakReference<>(x);
        final AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        ScheduledFuture<?> future = timer.scheduleAtFixedRate(() -> {
            ParameterWithIdRequestHelper pwirh = ref.get();
            if (pwirh == null) {
                ScheduledFuture<?> f = futureRef.get();
                f.cancel(false);
            } else {
                pwirh.checkPeriodicExpiration();
            }
        }, CHECK_EXPIRATION_INTERVAL, CHECK_EXPIRATION_INTERVAL, TimeUnit.MILLISECONDS);
        futureRef.set(future);
    }

    // turn NamedObjectId to Parameter references
    private List<Parameter> checkNames(List<NamedObjectId> plist) throws InvalidIdentification {
        List<Parameter> result = new ArrayList<>();
        List<NamedObjectId> invalid = new ArrayList<>(0);
        for (NamedObjectId id : plist) {
            try {
                Parameter p = prm.getParameter(id);
                result.add(p);
            } catch (InvalidIdentification e) {
                invalid.add(id);
            }
        }
        if (!invalid.isEmpty()) {
            log.info("Throwing invalid identification for the following items :{}", invalid);
            throw new InvalidIdentification(invalid);
        }
        return result;
    }

    public void removeRequest(int subscriptionId) {
        if (subscriptions.remove(subscriptionId) == null) {
            log.warn("remove requested for an invalid subscription id {}", subscriptionId);
            return;
        }
        prm.removeRequest(subscriptionId);
    }

    public void removeItemsFromRequest(int subscriptionId, List<NamedObjectId> parameterIds, User user)
            throws NoPermissionException {
        Subscription subscr = subscriptions.get(subscriptionId);

        if (subscr == null) {
            log.warn("remove requested for an invalid subscription id {}", subscriptionId);
            return;
        }
        List<Parameter> paramsToRemove = new ArrayList<>();
        synchronized (subscr) {
            for (NamedObjectId id : parameterIds) {
                Parameter p = subscr.remove(id);
                if (p != null) {
                    paramsToRemove.add(p);
                }
            }
        }
        if (!paramsToRemove.isEmpty()) {
            prm.removeItemsFromRequest(subscriptionId, paramsToRemove);
        }
    }

    public ParameterRequestManager getPrm() {
        return prm;
    }

    public int subscribeAll(String namespace, User user) throws NoPermissionException {
        checkParameterPrivilege(user, ".*");
        return prm.subscribeAll(this);
    }

    public List<ParameterValueWithId> getValuesFromCache(List<NamedObjectId> idList, User user)
            throws InvalidIdentification, NoPermissionException {
        List<Parameter> params = checkNames(idList);

        ListMultimap<Parameter, NamedObjectId> lm = ArrayListMultimap.create();
        for (int i = 0; i < idList.size(); i++) {
            Parameter p = params.get(i);
            checkParameterPrivilege(user, p.getQualifiedName());
            NamedObjectId id = idList.get(i);
            lm.put(p, id);
        }

        List<ParameterValue> values = prm.getValuesFromCache(params);
        List<ParameterValueWithId> plist = new ArrayList<>(values.size());

        for (ParameterValue pv : values) {
            List<NamedObjectId> l = lm.get(pv.getParameter());
            if (l == null) {
                log.warn("Received values for a parameter not requested: {}", pv.getParameter());
                continue;
            }

            for (NamedObjectId id : l) {
                ParameterValueWithId pvwi = new ParameterValueWithId(pv, id);
                plist.add(pvwi);
            }
        }

        return plist;
    }

    /**
     * Called from {@link ParameterListener when new parameters are available to be sent to clients}
     */
    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> items) {
        Subscription subscription = subscriptions.get(subscriptionId);
        if (subscription == null) { // probably the subscription has just been removed
            log.debug("Received an updateItems for an unknown subscription {}", subscriptionId);
            return;
        }

        List<ParameterValueWithId> plist = new ArrayList<>(items.size());
        synchronized (subscription) {
            if (subscription.checkExpiration) {
                long now = getAquisitionTime(items);

                List<ParameterValueWithId> expired = updateAndCheckExpiration(subscription, items, now);
                if (!expired.isEmpty()) {
                    log.debug("Updating {} parameters due to expiration", expired.size());
                    listener.update(subscriptionId, expired);
                }
            }

            for (ParameterValue pv : items) {
                addValueForAllIds(plist, subscription, pv);
            }
        }
        listener.update(subscriptionId, plist);
    }

    /**
     * Change processor and return the list of parameters that were valid in the old processor and are not anymore
     */
    public List<NamedObjectId> switchPrm(ParameterRequestManager newPrm, User user)
            throws NoPermissionException {
        List<NamedObjectId> invalid = new ArrayList<>();
        if (prm.getXtceDb() == newPrm.getXtceDb()) {
            for (int subscriptionId : subscriptions.keySet()) {
                List<Parameter> plist = prm.removeRequest(subscriptionId);
                // checking permission
                for (Parameter p : plist) {
                    checkParameterPrivilege(user, p.getQualifiedName());
                }
                newPrm.addRequest(subscriptionId, plist, this);
            }
            prm = newPrm;
        } else {
            // this is the tricky case: the XtceDB has changed so all Parameter references are invalid for the new
            // processor
            // we have to re-create the subscriptions starting from the original subscribed names
            // and take care that some names may have become invalid
            log.info("XtceDB has changed, recreating the parameter subscriptions");
            subscriptions.keySet().forEach(id -> prm.removeRequest(id));
            prm = newPrm;
            for (int subscriptionId : subscriptions.keySet()) {
                Subscription subscr = subscriptions.get(subscriptionId);
                synchronized (subscr) {
                    List<NamedObjectId> idList = subscr.getallIds();
                    List<Parameter> plist;
                    try {
                        plist = checkNames(idList);
                    } catch (InvalidIdentification e) {
                        log.warn("Got invalid identification when moving parameters to a new processor: {}",
                                e.getInvalidParameters());
                        idList.removeAll(e.getInvalidParameters());
                        invalid.addAll(e.getInvalidParameters());
                        try {
                            plist = checkNames(idList);
                        } catch (InvalidIdentification e1) { // shouldn't happen again
                            throw new IllegalStateException(e1);
                        }
                    }
                    assert (idList.size() == plist.size());
                    Subscription subscr1 = new Subscription(subscr.checkExpiration);

                    for (int i = 0; i < plist.size(); i++) {
                        Parameter p = plist.get(i);
                        checkParameterPrivilege(user, p.getQualifiedName());
                        NamedObjectId id = idList.get(i);
                        subscr1.put(p, id);
                    }
                    newPrm.addRequest(subscriptionId, plist, this);
                    subscriptions.put(subscriptionId, subscr1);
                }
            }
        }
        return invalid;
    }

    private long getAquisitionTime(List<ParameterValue> items) {
        for (ParameterValue pv : items) {
            if (pv.hasAcquisitionTime()) {
                return pv.getAcquisitionTime();
            }
        }
        return prm.yproc.getCurrentTime();
    }

    // adds the pv into plist with all ids subscribed
    private void addValueForAllIds(List<ParameterValueWithId> plist, Subscription subscription, ParameterValue pv) {
        Parameter p = pv.getParameter();
        List<NamedObjectId> idList = subscription.get(p);
        if (idList == null || idList.isEmpty()) {
            log.warn("Received values for a parameter not subscribed: {}", pv.getParameter());
            return;
        }

        for (NamedObjectId id : idList) {
            ParameterValueWithId pvwi = new ParameterValueWithId(pv, id);
            plist.add(pvwi);
        }
    }

    private void checkPeriodicExpiration() {
        for (Map.Entry<Integer, Subscription> me : subscriptions.entrySet()) {
            Subscription subscription = me.getValue();
            synchronized (subscription) {
                long now = prm.yproc.getCurrentTime();
                if ((subscription.checkExpiration)
                        && (now - subscription.lastExpirationCheck > CHECK_EXPIRATION_INTERVAL)) {
                    List<ParameterValueWithId> expired = checkExpiration(subscription, now);
                    if (!expired.isEmpty()) {
                        log.debug("Updating {} parameters due to expiration", expired.size());
                        listener.update(me.getKey(), expired);
                    }
                }
            }
        }
    }

    // update the expiration list with new values and check expiration of parameters that are just updating
    // in case the expiration is shorter than the check interval - this method would detect and send the parameters that
    // have just expired
    private List<ParameterValueWithId> updateAndCheckExpiration(Subscription subscription, List<ParameterValue> items,
            long now) {
        List<ParameterValueWithId> expired = new ArrayList<>();
        for (ParameterValue pv : items) {
            Parameter p = pv.getParameter();
            ParameterValue oldPv = subscription.pvexp.put(p, pv);
            if ((oldPv != null) && oldPv.getAcquisitionStatus() == AcquisitionStatus.ACQUIRED && oldPv.isExpired(now)) {
                ParameterValue tmp = new ParameterValue(oldPv); // make a copy because this is shared by other
                                                                // subscribers
                tmp.setAcquisitionStatus(AcquisitionStatus.EXPIRED);
                addValueForAllIds(expired, subscription, tmp);
            }
        }
        return expired;
    }

    // check expiration of all parameters from subscription
    private List<ParameterValueWithId> checkExpiration(Subscription subscription, long now) {
        List<ParameterValueWithId> expired = new ArrayList<>();
        for (ParameterValue pv : subscription.pvexp.values()) {
            if (pv.getAcquisitionStatus() == AcquisitionStatus.ACQUIRED && pv.isExpired(now)) {

                ParameterValue tmp = new ParameterValue(pv); // make a copy because this is shared by other subscribers
                tmp.setAcquisitionStatus(AcquisitionStatus.EXPIRED);
                subscription.pvexp.put(pv.getParameter(), tmp);
                addValueForAllIds(expired, subscription, tmp);
            }
        }
        subscription.lastExpirationCheck = now;
        return expired;
    }

    /**
     * Check if the user has a privilege for the specified parameter name
     * 
     * @param authToken
     * @param parameterName
     * @throws NoPermissionException
     */
    private void checkParameterPrivilege(User user, String parameterName)
            throws NoPermissionException {
        if (!user.hasObjectPrivilege(ObjectPrivilegeType.ReadParameter, parameterName)) {
            throw new NoPermissionException("User " + user + " has no permission for parameter " + parameterName);
        }
    }

    public void quit() {
        for (int subscriptionId : subscriptions.keySet()) {
            prm.removeRequest(subscriptionId);
        }
    }

    static class Subscription {
        Map<Parameter, List<NamedObjectId>> params = new HashMap<>();
        boolean checkExpiration = false;
        long lastExpirationCheck = -1;
        // contains the parameters that have an expiration time set
        Map<Parameter, ParameterValue> pvexp;

        public Subscription(boolean checkExpiration) {
            this.checkExpiration = checkExpiration;
            if (checkExpiration) {
                pvexp = new HashMap<>();
            }
        }

        public List<NamedObjectId> getallIds() {
            List<NamedObjectId> r = new ArrayList<>();
            for (List<NamedObjectId> l : params.values()) {
                r.addAll(l);
            }
            return r;
        }

        /**
         * looks and removes the id from a list and returns the associated parameter if there is no id mapped to it
         * anymore otherwise return null
         * 
         * @param id
         * @return
         */
        public Parameter remove(NamedObjectId id) {
            Parameter p = null;
            for (Map.Entry<Parameter, List<NamedObjectId>> me : params.entrySet()) {
                List<NamedObjectId> l = me.getValue();
                if (l.remove(id)) {
                    if (l.isEmpty()) {
                        p = me.getKey();
                    }
                    break;
                }
            }
            if (p != null) {
                params.remove(p);
                if (pvexp != null) {
                    pvexp.remove(p);
                }
            }
            return p;
        }

        public List<NamedObjectId> get(Parameter parameter) {
            return params.get(parameter);
        }

        public boolean put(Parameter p, NamedObjectId id) {
            List<NamedObjectId> l = params.get(p);
            if (l == null) {
                l = new ArrayList<>();
                params.put(p, l);
            } else if (l.contains(id)) {
                return false;
            }
            l.add(id);

            return true;
        }
    }
}
