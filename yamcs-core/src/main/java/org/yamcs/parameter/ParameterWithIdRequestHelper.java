package org.yamcs.parameter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.User;
import org.yamcs.utils.AggregateUtil;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.PathElement;

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
    static Logger log = LoggerFactory.getLogger(ParameterWithIdRequestHelper.class.getName());
    Map<Integer, Subscription> subscriptions = new ConcurrentHashMap<>();

    // how often to check expiration
    private static long CHECK_EXPIRATION_INTERVAL = 1000;
    final static ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    int subscribeAllId = -1;

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
        List<ParameterWithId> plist = checkNames(idList);
        Subscription subscr = new Subscription(checkExpiration);
        for (int i = 0; i < idList.size(); i++) {
            checkParameterPrivilege(user, plist.get(i).p.getQualifiedName());
            subscr.add(plist.get(i));
        }
        int subscriptionId = prm.addRequest(plist.stream().map(pwid -> pwid.p).collect(Collectors.toList()), this);
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
        List<ParameterWithId> plist = checkNames(idList);
        synchronized (subscr) {
            for (int i = 0; i < idList.size(); i++) {
                Parameter p = plist.get(i).p;
                checkParameterPrivilege(user, p.getQualifiedName());
                NamedObjectId id = idList.get(i);
                if (!subscr.add(plist.get(i))) {
                    log.info("Ignoring duplicate subscription for '{}', id: {}", p.getName(),
                            StringConverter.idToString(id));
                }
            }
        }
        prm.addItemsToRequest(subscriptionId, plist.stream().map(pwid -> pwid.p).collect(Collectors.toList()));
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

    List<ParameterWithId> checkNames(List<NamedObjectId> idList) throws InvalidIdentification {
        return checkNames(prm, idList);
    }

    // turn NamedObjectId to Parameter references
    public static ParameterWithId checkName(ParameterRequestManager prm, NamedObjectId id)
            throws InvalidIdentification {
        String name = id.getName();
        int x = AggregateUtil.findSeparator(name);
        NamedObjectId id1;
        PathElement[] path;
        if (x > 0) { // this is an array or aggregate element
            id1 = NamedObjectId.newBuilder(id).setName(name.substring(0, x)).build();
            try {
                path = AggregateUtil.parseReference(name.substring(x));
            } catch (IllegalArgumentException e) {
                throw new InvalidIdentification(id);
            }
        } else {
            id1 = id;
            path = null;
        }
        Parameter p = prm.getParameter(id1);
        if (path != null) {
            if (!AggregateUtil.verifyPath(p.getParameterType(), path)) {
                throw new InvalidIdentification(id);
            }
        }
        return new ParameterWithId(p, id, path);

    }

    // turn NamedObjectId to Parameter references
    public static List<ParameterWithId> checkNames(ParameterRequestManager prm, List<NamedObjectId> idList)
            throws InvalidIdentification {
        List<ParameterWithId> result = new ArrayList<>();
        List<NamedObjectId> invalid = new ArrayList<>(0);
        for (NamedObjectId id : idList) {
            try {
                result.add(checkName(prm, id));
            } catch (InvalidIdentification e) {
                invalid.add(id);
                continue;
            }

        }
        if (!invalid.isEmpty()) {
            log.info("Throwing invalid identification for the following items: {}", invalid);
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

    public int subscribeAll(User user) throws NoPermissionException {
        checkParameterPrivilege(user, ".*");
        subscribeAllId = prm.subscribeAll(this);
        return subscribeAllId;
    }

    /**
     * retrieve the subscribed values from cache
     * 
     * @param subscriptionId
     * @return
     */
    public List<ParameterValueWithId> getValuesFromCache(int subscriptionId) {
        Subscription subscr = subscriptions.get(subscriptionId);
        if (subscr == null) {
            log.warn("add item requested for an invalid subscription id {}", subscriptionId);
            throw new InvalidRequestIdentification("Invalid subcription id", subscriptionId);
        }
        long now = prm.processor.getCurrentTime();

        List<ParameterValue> values = prm.getValuesFromCache(subscr.params.keySet());
        List<ParameterValueWithId> pvlist = new ArrayList<>(values.size());
        for (ParameterValue pv : values) {
            if (pv.isExpired(now)) {
                pv = new ParameterValue(pv);
                pv.setAcquisitionStatus(AcquisitionStatus.EXPIRED);
            }
            if (subscr.checkExpiration && pv.hasExpirationTime()) {
                subscr.pvexp.put(pv.getParameter(), pv);
            }
            List<ParameterWithId> l = subscr.params.get(pv.getParameter());
            if (l == null) {
                log.warn("Received values for a parameter not requested: {}", pv.getParameter());
                continue;
            }
            addValueForAllIds(pvlist, l, pv);
        }

        return pvlist;
    }

    /**
     * Retrieve a list of parameter values from cache. This call does not block.
     * 
     * @param idList
     * @param user
     * @return
     * @throws InvalidIdentification
     * @throws NoPermissionException
     */
    public List<ParameterValueWithId> getValuesFromCache(List<NamedObjectId> idList, User user)
            throws InvalidIdentification, NoPermissionException {
        List<ParameterWithId> plist = checkNames(idList);

        ListMultimap<Parameter, ParameterWithId> lm = ArrayListMultimap.create();
        for (int i = 0; i < idList.size(); i++) {
            ParameterWithId pwid = plist.get(i);
            checkParameterPrivilege(user, pwid.p.getQualifiedName());
            lm.put(pwid.p, pwid);
        }

        List<ParameterValue> values = prm
                .getValuesFromCache(plist.stream().map(pwid -> pwid.p).distinct().collect(Collectors.toList()));
        List<ParameterValueWithId> pvlist = new ArrayList<>(values.size());

        for (ParameterValue pv : values) {
            List<ParameterWithId> l = lm.get(pv.getParameter());
            if (l == null) {
                log.warn("Received values for a parameter not requested: {}", pv.getParameter());
                continue;
            }
            addValueForAllIds(pvlist, l, pv);
        }

        return pvlist;
    }

    // adds the pv into plist with all ids from idList
    private void addValueForAllIds(List<ParameterValueWithId> plist, List<ParameterWithId> idList, ParameterValue pv) {
        for (ParameterWithId pwid : idList) {
            ParameterValue pv1 = null;
            if (pwid.path != null) {
                try {
                    pv1 = AggregateUtil.extractMember(pv, pwid.path);
                    if (pv1 == null) { // could be that we reference an element of an array that doesn't exist
                        continue;
                    }
                } catch (Exception e) {
                    log.error("Failed to extract {} from parameter value {}", Arrays.toString(pwid.path), pv, e);
                    continue;
                }
            } else {
                pv1 = pv;
            }

            ParameterValueWithId pvwi = new ParameterValueWithId(pv1, pwid.id);
            plist.add(pvwi);
        }
    }

    /**
     * Called from {@link ParameterRequestManager when new parameters are available to be sent to clients}
     */
    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> items) {
        if (subscriptionId == subscribeAllId) {
            updateAllSubscription(subscriptionId, items);
            return;
        }
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
                addValueForAllSubscribedIds(plist, subscription, pv);
            }
        }
        listener.update(subscriptionId, plist);
    }

    private void updateAllSubscription(int subscriptionId, List<ParameterValue> items) {
        List<ParameterValueWithId> plist = new ArrayList<>(items.size());
        for (ParameterValue pv : items) {
            plist.add(new ParameterValueWithId(pv,
                    NamedObjectId.newBuilder().setName(pv.getParameterQualifiedName()).build()));
        }
        listener.update(subscriptionId, plist);
    }

    public void unselectPrm() {
        for (int subscriptionId : subscriptions.keySet()) {
            prm.removeRequest(subscriptionId);
        }
        prm = null;
    }

    public List<NamedObjectId> selectPrm(ParameterRequestManager prm, User user) throws NoPermissionException {
        List<NamedObjectId> invalid = new ArrayList<>();
        // Parameter references may be invalid for the new processor
        // we have to re-create the subscriptions starting from the original subscribed names
        // and take care that some names may have become invalid
        this.prm = prm;
        for (int subscriptionId : subscriptions.keySet()) {
            Subscription subscr = subscriptions.get(subscriptionId);
            synchronized (subscr) {
                List<NamedObjectId> idList = subscr.getallIds();
                List<ParameterWithId> plist;
                try {
                    plist = checkNames(idList);
                } catch (InvalidIdentification e) {
                    log.warn("Got invalid identification when selecting parameters for processor {}: {}",
                            prm.processor.getName(), e.getInvalidParameters());
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
                    ParameterWithId pwid = plist.get(i);
                    checkParameterPrivilege(user, pwid.p.getQualifiedName());
                    subscr1.add(pwid);
                }
                prm.addRequest(subscriptionId, plist.stream().map(pwid -> pwid.p).collect(Collectors.toList()), this);
                subscriptions.put(subscriptionId, subscr1);
            }
        }
        return invalid;
    }

    /**
     * Change processor and return the list of parameters that were valid in the old processor and are not anymore
     */
    public List<NamedObjectId> switchPrm(ParameterRequestManager newPrm, User user) throws NoPermissionException {
        if (prm != null) {
            unselectPrm();
        }
        if (newPrm != null) {
            return selectPrm(newPrm, user);
        } else {
            return Collections.emptyList();
        }
    }

    private long getAquisitionTime(List<ParameterValue> items) {
        for (ParameterValue pv : items) {
            if (pv.hasAcquisitionTime()) {
                return pv.getAcquisitionTime();
            }
        }
        return prm.processor.getCurrentTime();
    }

    // adds the pv into plist with all ids subscribed
    private void addValueForAllSubscribedIds(List<ParameterValueWithId> plist, Subscription subscription,
            ParameterValue pv) {
        Parameter p = pv.getParameter();
        List<ParameterWithId> idList = subscription.get(p);
        if (idList == null || idList.isEmpty()) {
            log.warn("Received values for a parameter not subscribed: {}", pv.getParameter());
            return;
        }
        addValueForAllIds(plist, idList, pv);
    }

    private void checkPeriodicExpiration() {
        for (Map.Entry<Integer, Subscription> me : subscriptions.entrySet()) {
            Subscription subscription = me.getValue();
            synchronized (subscription) {
                long now = prm.processor.getCurrentTime();
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
            ParameterValue oldPv;
            if (pv.hasExpirationTime()) {
                oldPv = subscription.pvexp.put(p, pv);
            } else {
                oldPv = subscription.pvexp.remove(p);
            }
            if ((oldPv != null) && oldPv.getAcquisitionStatus() == AcquisitionStatus.ACQUIRED && oldPv.isExpired(now)) {
                ParameterValue tmp = new ParameterValue(oldPv); // make a copy because this is shared by other
                                                                // subscribers
                tmp.setAcquisitionStatus(AcquisitionStatus.EXPIRED);
                addValueForAllSubscribedIds(expired, subscription, tmp);
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
                addValueForAllSubscribedIds(expired, subscription, tmp);
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
        subscriptions.clear();
        if (subscribeAllId != -1) {
            prm.unsubscribeAll(subscribeAllId);
        }
    }

    static class Subscription {
        Map<Parameter, List<ParameterWithId>> params = new LinkedHashMap<>();
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
            for (List<ParameterWithId> l : params.values()) {
                for (ParameterWithId pwid : l) {
                    r.add(pwid.id);
                }
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
            boolean found = false;
            for (Map.Entry<Parameter, List<ParameterWithId>> me : params.entrySet()) {
                List<ParameterWithId> l = me.getValue();
                for (ParameterWithId pwid : l) {
                    if (pwid.id.equals(id)) {
                        l.remove(pwid);
                        found = true;
                        break;
                    }
                }
                if (found) {
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

        public List<ParameterWithId> get(Parameter parameter) {
            return params.get(parameter);
        }

        public boolean add(ParameterWithId pwid) {
            List<ParameterWithId> l = params.get(pwid.p);
            if (l == null) {
                l = new ArrayList<>();
                params.put(pwid.p, l);
            } else if (l.stream().anyMatch(pwid1 -> pwid1.id.equals(pwid.id))) {
                return false;
            }
            l.add(pwid);

            return true;
        }
    }
}
