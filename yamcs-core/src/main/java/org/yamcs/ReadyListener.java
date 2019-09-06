package org.yamcs;

/**
 * Listener that is called when Yamcs has fully started. Register instances at
 * {@link YamcsServer#addReadyListener(ReadyListener)}
 */
@FunctionalInterface
public interface ReadyListener {

    public void onReady();
}
