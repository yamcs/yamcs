package org.yamcs;

/**
 * Listener that is called when Yamcs has fully started. Register instances at
 * {@link YamcsServer#addStartListener(StartListener)}
 */
@FunctionalInterface
public interface StartListener {

    public void onStart();
}
