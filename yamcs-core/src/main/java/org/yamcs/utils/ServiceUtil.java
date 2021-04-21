package org.yamcs.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.UncheckedExecutionException;

public class ServiceUtil {

    /**
     * Awaits a service to be running but throw an {@link UncheckedExecutionException} instead of an
     * {@link IllegalStateException}
     * <p>
     * To be used when the only reason for a service not to be in the RUNNING state is because it failed to start (not
     * because an illegal state transition has been attempted)
     * 
     * @param service
     */
    public static void awaitServiceRunning(Service service) {
        try {
            service.awaitRunning();
        } catch (IllegalStateException e) {
            throw new UncheckedExecutionException(ExceptionUtil.unwind(e.getCause()));
        }
    }

    public static void awaitServiceTerminated(Service service, int numSeconds, Log log) {
        try {
            service.awaitTerminated(numSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Service {} did not stop in {} seconds", service.getClass().getName(),
                    YamcsServer.SERVICE_STOP_GRACE_TIME);
        } catch (IllegalStateException e) {
            log.error("Service {} was in a bad state: {}", service.getClass().getName(), e.toString());
        }
    }
}
