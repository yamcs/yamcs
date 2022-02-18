package org.yamcs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.yamcs.protobuf.YamcsInstance.InstanceState;

import com.google.common.util.concurrent.Monitor;
import com.google.common.util.concurrent.Monitor.Guard;

/**
 * Inspired from Guava services, this class offers the following states:
 * <ul>
 * <li>OFFLINE</li>
 * <li>INITIALIZING</li>
 * <li>INITIALIZED</li>
 * <li>STARTING</li>
 * <li>RUNNING</li>
 * <li>STOPPING</li>
 * <li>FAILED</li>
 * </ul>
 * 
 * transitions are allowed back to OFFLINE from all steady states
 * 
 * @author nm
 *
 */
public abstract class YamcsInstanceService {
    private final Monitor monitor = new Monitor();

    private final Guard isInitializable = new IsInitializableGuard();
    private final Guard isStartable = new IsStartableGuard();
    private final Guard isStoppable = new IsStoppableGuard();
    private final Guard hasReachedRunning = new HasReachedRunningGuard();
    private final Guard hasReachedOffline = new HasReachedOfflineGuard();
    private final Guard hasReachedInitialized = new HasReachedInitializedGuard();

    private volatile StateSnapshot snapshot = new StateSnapshot(InstanceState.OFFLINE);

    private Set<InstanceStateListener> stateListeners = new CopyOnWriteArraySet<>();

    protected abstract void doInit();

    protected abstract void doStart();

    protected abstract void doStop();

    public final InstanceState state() {
        return snapshot.externalState();
    }

    public final YamcsInstanceService initAsync() {
        if (monitor.enterIf(isInitializable)) {
            try {
                snapshot = new StateSnapshot(InstanceState.INITIALIZING);
                initializing();
                doInit();
            } catch (Throwable startupFailure) {
                notifyFailed(startupFailure);
            } finally {
                monitor.leave();
                // executeListeners();
            }
        } else {
            throw new IllegalStateException("Service " + this + " has already been started");
        }
        return this;
    }

    public final YamcsInstanceService startAsync() {
        if (monitor.enterIf(isStartable)) {
            try {
                InstanceState previous = state();
                if (previous == InstanceState.INITIALIZING) {
                    snapshot = new StateSnapshot(InstanceState.STARTING, true, false, null);
                } else {
                    snapshot = new StateSnapshot(InstanceState.STARTING);
                }
                starting();
                doStart();
            } catch (Throwable startupFailure) {
                notifyFailed(startupFailure);
            } finally {
                monitor.leave();
                // executeListeners();
            }
        } else {
            throw new IllegalStateException("Service " + this + " has already been started");
        }
        return this;
    }

    public final YamcsInstanceService stopAsync() {
        if (monitor.enterIf(isStoppable)) {
            try {
                InstanceState previous = state();
                switch (previous) {
                case INITIALIZED:
                    snapshot = new StateSnapshot(InstanceState.OFFLINE);
                    offline(InstanceState.INITIALIZED);
                    break;
                case STARTING:
                case INITIALIZING:
                    snapshot = new StateSnapshot(previous, false, true, null);
                    stopping(previous);
                    break;
                case RUNNING:
                    snapshot = new StateSnapshot(InstanceState.STOPPING);
                    stopping(InstanceState.RUNNING);
                    doStop();
                    break;
                case OFFLINE:
                    break;
                case FAILED:
                    snapshot = new StateSnapshot(InstanceState.OFFLINE);
                    break;
                case STOPPING:
                    // These cases are impossible due to the if statement above.
                    throw new AssertionError("isStoppable is incorrectly implemented, saw: " + previous);
                default:
                    throw new AssertionError("Unexpected state: " + previous);
                }
            } catch (Throwable shutdownFailure) {
                notifyFailed(shutdownFailure);
            } finally {
                monitor.leave();
                // executeListeners();
            }
        }
        return this;
    }

    public final void awaitInitialized() {
        monitor.enterWhenUninterruptibly(hasReachedInitialized);
        try {
            checkCurrentState(InstanceState.INITIALIZED);
        } finally {
            monitor.leave();
        }
    }

    public final void awaitRunning() {
        monitor.enterWhenUninterruptibly(hasReachedRunning);
        try {
            checkCurrentState(InstanceState.RUNNING);
        } finally {
            monitor.leave();
        }
    }

    public final void awaitOffline() {
        monitor.enterWhenUninterruptibly(hasReachedOffline);
        try {
            checkCurrentState(InstanceState.OFFLINE);
        } finally {
            monitor.leave();
        }
    }

    public void addStateListener(InstanceStateListener listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(InstanceStateListener listener) {
        stateListeners.remove(listener);
    }

    /**
     * Implementing classes should invoke this method once their service has been initialized.
     *
     * @throws IllegalStateException
     *             if the service is not {@link InstanceState#STARTING}.
     */
    protected final void notifyInitialized() {
        monitor.enter();
        try {
            // We have to examine the internal state of the snapshot here to properly handle the stop
            // while starting case.
            if (snapshot.state != InstanceState.INITIALIZING) {
                IllegalStateException failure = new IllegalStateException(
                        "Cannot notifyInitialized() when the service is " + snapshot.state);
                notifyFailed(failure);
                throw failure;
            }

            if (snapshot.shutdownWhenStartupOrInitFinishes) {
                snapshot = new StateSnapshot(InstanceState.STOPPING);
                // We don't call listeners here because we already did that when we set the
                // shutdownWhenStartupFinishes flag.
                doStop();
            } else if (snapshot.runWhenInitFinishes) {
                snapshot = new StateSnapshot(InstanceState.STARTING);
                doStart();
            } else {
                snapshot = new StateSnapshot(InstanceState.INITIALIZED);
                running();
            }
        } finally {
            monitor.leave();
            // executeListeners();
        }
    }

    /**
     * Implementing classes should invoke this method once their service has started. It will cause the service to
     * transition from {@link InstanceState#STARTING} to {@link InstanceState#RUNNING}.
     *
     * @throws IllegalStateException
     *             if the service is not {@link InstanceState#STARTING}.
     */
    protected final void notifyStarted() {
        monitor.enter();
        try {
            // We have to examine the internal state of the snapshot here to properly handle the stop
            // while starting case.
            if (snapshot.state != InstanceState.STARTING) {
                IllegalStateException failure = new IllegalStateException(
                        "Cannot notifyStarted() when the service is " + snapshot.state);
                notifyFailed(failure);
                throw failure;
            }

            if (snapshot.shutdownWhenStartupOrInitFinishes) {
                snapshot = new StateSnapshot(InstanceState.STOPPING);
                // We don't call listeners here because we already did that when we set the
                // shutdownWhenStartupFinishes flag.
                doStop();
            } else {
                snapshot = new StateSnapshot(InstanceState.RUNNING);
                running();
            }
        } finally {
            monitor.leave();
            // executeListeners();
        }
    }

    private void running() {
        stateListeners.forEach(InstanceStateListener::running);
    }

    /**
     * Implementing classes should invoke this method once their service has stopped. It will cause the service to
     * transition from {@link InstanceState#STOPPING} to {@link InstanceState#OFFLINE}.
     *
     * @throws IllegalStateException
     *             if the service is neither {@link InstanceState#STOPPING} nor {@link InstanceState#RUNNING}.
     */
    protected final void notifyStopped() {
        monitor.enter();
        try {
            // We check the internal state of the snapshot instead of state() directly so we don't allow
            // notifyStopped() to be called while STARTING, even if stop() has already been called.
            InstanceState previous = snapshot.state;
            if (previous != InstanceState.STOPPING && previous != InstanceState.RUNNING) {
                IllegalStateException failure = new IllegalStateException(
                        "Cannot notifyStopped() when the service is " + previous);
                notifyFailed(failure);
                throw failure;
            }
            snapshot = new StateSnapshot(InstanceState.OFFLINE);
            offline(previous);
        } finally {
            monitor.leave();
            // executeListeners();
        }
    }

    private void checkCurrentState(InstanceState expected) {
        InstanceState actual = state();
        if (actual != expected) {
            if (actual == InstanceState.FAILED) {
                // Handle this specially so that we can include the failureCause, if there is one.
                throw new IllegalStateException("Expected the service to be " + expected
                        + ", but the service has FAILED", failureCause());
            }
            throw new IllegalStateException("Expected the service to be " + expected + ", but was "
                    + actual);
        }
    }

    public final Throwable failureCause() {
        return snapshot.failureCause();
    }

    /**
     * Invoke this method to transition the service to the {@link InstanceState#FAILED}. The service will <b>not be
     * stopped</b> if it is running. Invoke this method when a service has failed critically or otherwise cannot be
     * started nor stopped.
     */
    protected final void notifyFailed(Throwable cause) {
        checkNotNull(cause);

        monitor.enter();
        try {
            InstanceState previous = state();
            switch (previous) {
            case INITIALIZED:
            case OFFLINE:
                throw new IllegalStateException("Failed while in state:" + previous, cause);
            case RUNNING:
            case STARTING:
            case STOPPING:
            case INITIALIZING:
                snapshot = new StateSnapshot(InstanceState.FAILED, false, false, cause);
                failed(previous, cause);
                break;
            case FAILED:
                // Do nothing
                break;
            default:
                throw new AssertionError("Unexpected state: " + previous);
            }
        } finally {
            monitor.leave();
            // executeListeners();
        }
    }

    private void failed(InstanceState previous, Throwable cause) {
        stateListeners.forEach(l -> l.failed(cause));
    }

    private void initializing() {
        stateListeners.forEach(InstanceStateListener::initializing);
    }

    private void starting() {
        stateListeners.forEach(InstanceStateListener::starting);
    }

    public void offline(InstanceState from) {
        stateListeners.forEach(InstanceStateListener::offline);
    }

    public void stopping(InstanceState from) {
        stateListeners.forEach(l -> l.stopping());
    }

    /**
     * An immutable snapshot of the current state of the service. This class represents a consistent snapshot of the
     * state and therefore it can be used to answer simple queries without needing to grab a lock.
     */
    private static final class StateSnapshot {
        /**
         * The internal state, which equals external state unless shutdownWhenStartupFinishes is true.
         */
        final InstanceState state;

        /**
         * If true, the user requested a start while the service was still initializing up.
         */
        final boolean runWhenInitFinishes;

        /**
         * If true, the user requested a shutdown while the service was still starting or initializing up.
         */
        final boolean shutdownWhenStartupOrInitFinishes;

        /**
         * The exception that caused this service to fail. This will be {@code null} unless the service has failed.
         */
        final Throwable failure;

        StateSnapshot(InstanceState internalState) {
            this(internalState, false, false, null);
        }

        StateSnapshot(
                InstanceState internalState, boolean runWhenInitFinishes, boolean shutdownWhenStartupFinishes,
                Throwable failure) {
            checkArgument(!shutdownWhenStartupFinishes || internalState == InstanceState.STARTING,
                    "shudownWhenStartupFinishes can only be set if state is STARTING. Got %s instead.",
                    internalState);
            checkArgument(!(failure != null ^ internalState == InstanceState.FAILED),
                    "A failure cause should be set if and only if the state is failed.  Got %s and %s "
                            + "instead.",
                    internalState, failure);
            this.state = internalState;
            this.shutdownWhenStartupOrInitFinishes = shutdownWhenStartupFinishes;
            this.runWhenInitFinishes = runWhenInitFinishes;
            this.failure = failure;
        }

        /** @see Service#state() */
        InstanceState externalState() {
            if (shutdownWhenStartupOrInitFinishes && state == InstanceState.STARTING) {
                return InstanceState.STOPPING;
            } else {
                return state;
            }
        }

        /** @see Service#failureCause() */
        Throwable failureCause() {
            checkState(state == InstanceState.FAILED,
                    "failureCause() is only valid if the service has failed, service is %s", state);
            return failure;
        }
    }

    private final class IsStartableGuard extends Guard {
        IsStartableGuard() {
            super(monitor);
        }

        @Override
        public boolean isSatisfied() {
            return state().compareTo(InstanceState.INITIALIZED) <= 0;
        }
    }

    private final class IsStoppableGuard extends Guard {
        IsStoppableGuard() {
            super(monitor);
        }

        @Override
        public boolean isSatisfied() {
            InstanceState cs = state();
            return cs.compareTo(InstanceState.RUNNING) <= 0 || cs == InstanceState.FAILED;
        }
    }

    private final class IsInitializableGuard extends Guard {
        IsInitializableGuard() {
            super(monitor);
        }

        @Override
        public boolean isSatisfied() {
            return state() == InstanceState.OFFLINE;
        }
    }

    private final class HasReachedRunningGuard extends Guard {
        HasReachedRunningGuard() {
            super(monitor);
        }

        @Override
        public boolean isSatisfied() {
            return state().compareTo(InstanceState.RUNNING) >= 0;
        }
    }

    private final class HasReachedOfflineGuard extends Guard {
        HasReachedOfflineGuard() {
            super(monitor);
        }

        @Override
        public boolean isSatisfied() {
            return state() == InstanceState.OFFLINE;
        }
    }

    private final class HasReachedInitializedGuard extends Guard {
        HasReachedInitializedGuard() {
            super(monitor);
        }

        @Override
        public boolean isSatisfied() {
            return state().compareTo(InstanceState.INITIALIZED) >= 0;
        }
    }
}
