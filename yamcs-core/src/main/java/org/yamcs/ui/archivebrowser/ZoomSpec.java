package org.yamcs.ui.archivebrowser;

import org.yamcs.ui.archivebrowser.ArchivePanel.IndexChunkSpec;
import org.yamcs.utils.TimeEncoding;

public class ZoomSpec {
    long startInstant, stopInstant; //the start and stop of all the visible data (i.e. scrolling to the left and right)
    long selectionStart, selectionStop; //optional start/stop of the selection before next zoom in (to restore on zoomout)
    long viewLocation;
    long viewTimeWindow; //the total time visible at one time(i.e. if the scroll is not used)
    double pixelRatio; // ms per pixel

    ZoomSpec(long start, long stop, int pixelwidth, long viewTimeWindow) {
        this.startInstant = start;
        this.stopInstant = stop;
        this.viewTimeWindow = viewTimeWindow;
        viewLocation = start;
        setPixels(pixelwidth);
        selectionStart = TimeEncoding.INVALID_INSTANT;
        selectionStop = TimeEncoding.INVALID_INSTANT;
    }

    void setSelectedRange(long selectionStart, long selectionStop) {
        this.selectionStart = selectionStart;
        this.selectionStop = selectionStop;
    }

    void setPixels(int pixelwidth) {
        pixelRatio = (double)(viewTimeWindow) / pixelwidth; // ms per pixel
    }

    int getPixels() {
        return convertInstantToPixel(stopInstant);
    }

    int convertInstantToPixel(long ms) {
        return (int)Math.round((ms - startInstant) / pixelRatio);
    }

    long convertPixelToInstant(int x) {
        return (long)(x * pixelRatio) + startInstant;
    }

    public IndexChunkSpec convertPixelToChunk(int x) {
        return new IndexChunkSpec((long)(x * pixelRatio) + startInstant, (long)((x+1) * pixelRatio) + startInstant-1, 0, null);
    }
    
    @Override
    public String toString() {
        return "start: "+startInstant+" stop:"+stopInstant+" viewTimeWindow: "+viewTimeWindow+" pixelRatio: "+pixelRatio;
    }
}
