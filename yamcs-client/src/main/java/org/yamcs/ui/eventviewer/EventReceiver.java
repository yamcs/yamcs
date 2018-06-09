package org.yamcs.ui.eventviewer;

public interface EventReceiver {
    public void setEventViewer(EventViewer ev);
    public void retrievePastEvents();
}