package org.yamcs.tctm;

import org.yamcs.TmPacketProvider;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplayStatus.ReplayState;

public interface ArchiveTmPacketProvider extends TmPacketProvider {

    public void seek(long time, boolean autostart);

    public void pause();

    public void resume();

    public void changeSpeed(ReplaySpeed speed);

    public void changeEndAction(EndAction endAction);

    public void changeRange(long start, long stop);

    public ReplayState getReplayState();

    public ReplaySpeed getSpeed();

    public ReplayRequest getCurrentReplayRequest();

    public ReplayRequest getReplayRequest();

    public long getReplayTime();
}
