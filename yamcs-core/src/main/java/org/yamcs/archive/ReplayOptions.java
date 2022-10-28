package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.CommandHistoryReplayRequest;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.EventReplayRequest;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.PpReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.utils.TimeEncoding;

public class ReplayOptions {
    long rangeStart = TimeEncoding.INVALID_INSTANT;
    long rangeStop = TimeEncoding.INVALID_INSTANT;
    long playFrom = TimeEncoding.INVALID_INSTANT;
    EndAction endAction;
    SpeedSpec speed;
    boolean reverse;
    boolean autostart = true;

    // if all request objects are null -> replay all
    // if at least one of them is not null -> replay that type
    // if not null but empty -> means replay all of that type (e.g. all PP)
    // if not null and not empty -> use it as a filter
    private PpReplayRequest ppRequest;
    private PacketReplayRequest packetReplayRequest;
    private ParameterReplayRequest parameterReplayRequest;
    private CommandHistoryReplayRequest commandHistoryReplayRequest;
    private EventReplayRequest eventReplayRequest;

    public ReplayOptions(ReplayRequest protoRequest) {
        if (protoRequest.hasStart()) {
            rangeStart = TimeEncoding.fromProtobufTimestamp(protoRequest.getStart());
        }
        if (protoRequest.hasStop()) {
            rangeStop = TimeEncoding.fromProtobufTimestamp(protoRequest.getStop());
        }
        endAction = protoRequest.getEndAction();
        if (protoRequest.hasSpeed()) {
            speed = SpeedSpec.fromProtobuf(protoRequest.getSpeed());
        } else {
            speed = new SpeedSpec(SpeedSpec.Type.ORIGINAL, 1);
        }
        reverse = protoRequest.hasReverse() && protoRequest.getReverse();

        if (protoRequest.hasPacketRequest()) {
            this.packetReplayRequest = protoRequest.getPacketRequest();
        }

        if (protoRequest.hasPpRequest()) {
            this.ppRequest = protoRequest.getPpRequest();
        }
        autostart = protoRequest.getAutostart();
        this.playFrom = reverse ? this.rangeStop : this.rangeStart;
    }

    public ReplayOptions(long start, long stop, boolean reverse) {
        this.rangeStart = start;
        this.rangeStop = stop;
        this.reverse = reverse;
        this.playFrom = reverse ? this.rangeStop : this.rangeStart;
    }

    public ReplayOptions() {
    }

    public ReplayOptions(ReplayOptions other) {
        this.rangeStart = other.rangeStart;
        this.rangeStop = other.rangeStop;
        this.speed = other.speed;
        this.endAction = other.endAction;
        this.reverse = other.reverse;
        this.autostart = other.autostart;
        this.playFrom = other.playFrom;

        this.packetReplayRequest = other.packetReplayRequest;
        this.ppRequest = other.ppRequest;
        this.parameterReplayRequest = other.parameterReplayRequest;
        this.commandHistoryReplayRequest = other.commandHistoryReplayRequest;
        this.eventReplayRequest = other.eventReplayRequest;
    }

    public static ReplayOptions getAfapReplay(long start, long stop, boolean reverse) {
        ReplayOptions repl = new ReplayOptions(start, stop, reverse);
        repl.setSpeed(new SpeedSpec(SpeedSpec.Type.AFAP));
        repl.setEndAction(EndAction.QUIT);
        return repl;
    }

    public static ReplayOptions getAfapReplay() {
        ReplayOptions repl = new ReplayOptions();
        repl.setSpeed(new SpeedSpec(SpeedSpec.Type.AFAP));
        repl.setEndAction(EndAction.QUIT);
        return repl;
    }

    public void setSpeed(SpeedSpec speed) {
        this.speed = speed;
    }

    public void setRangeStart(long start) {
        this.rangeStart = start;
    }

    public void setRangeStop(long stop) {
        this.rangeStop = stop;
    }

    public void setPlayFrom(long playFrom) {
        this.playFrom = playFrom;
    }

    public SpeedSpec getSpeed() {
        return speed;
    }

    public long getPlayFrom() {
        return playFrom;
    }

    public boolean isReverse() {
        return reverse;
    }

    public ReplayRequest toProtobuf() {
        ReplayRequest.Builder rr = ReplayRequest.newBuilder();
        if (rangeStart != TimeEncoding.INVALID_INSTANT) {
            rr.setStart(TimeEncoding.toProtobufTimestamp(rangeStart));
        }
        if (rangeStop != TimeEncoding.INVALID_INSTANT) {
            rr.setStop(TimeEncoding.toProtobufTimestamp(rangeStop));
        }
        rr.setSpeed(speed.toProtobuf());
        rr.setEndAction(endAction);
        rr.setReverse(reverse);
        rr.setAutostart(autostart);
        if (packetReplayRequest != null) {
            rr.setPacketRequest(packetReplayRequest);
        }
        if (ppRequest != null) {
            rr.setPpRequest(ppRequest);
        }
        if (parameterReplayRequest != null) {
            rr.setParameterRequest(parameterReplayRequest);
        }

        if (commandHistoryReplayRequest != null) {
            rr.setCommandHistoryRequest(commandHistoryReplayRequest);
        }

        if (eventReplayRequest != null) {
            rr.setEventRequest(eventReplayRequest);
        }

        return rr.build();
    }

    public boolean isAutostart() {
        return autostart;
    }

    public EndAction getEndAction() {
        return endAction;
    }

    public boolean hasCommandHistoryRequest() {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean hasPpRequest() {
        return ppRequest != null;
    }

    public boolean hasEventRequest() {
        return false;
    }

    public long getRangeStart() {
        return rangeStart;
    }

    public long getRangeStop() {
        return rangeStop;
    }

    public boolean hasPlayFrom() {
        return playFrom != TimeEncoding.INVALID_INSTANT;
    }

    public boolean hasRangeStart() {
        return rangeStart != TimeEncoding.INVALID_INSTANT;
    }

    public boolean hasRangeStop() {
        return rangeStop != TimeEncoding.INVALID_INSTANT;
    }

    public void setEndAction(EndAction endAction) {
        this.endAction = endAction;
    }

    public PpReplayRequest getPpRequest() {
        return ppRequest == null ? PpReplayRequest.getDefaultInstance() : ppRequest;
    }

    public void setPpRequest(PpReplayRequest ppRequest) {
        this.ppRequest = ppRequest;
    }

    public PacketReplayRequest getPacketRequest() {
        return packetReplayRequest == null ? PacketReplayRequest.getDefaultInstance() : packetReplayRequest;
    }

    public boolean hasPacketRequest() {
        return packetReplayRequest != null;
    }

    public void setPacketRequest(PacketReplayRequest packetReplayRequest) {
        this.packetReplayRequest = packetReplayRequest;
    }

    public ParameterReplayRequest getParameterRequest() {
        return parameterReplayRequest == null ? ParameterReplayRequest.getDefaultInstance() : parameterReplayRequest;
    }

    public void setParameterRequest(ParameterReplayRequest parameterReplayRequest) {
        this.parameterReplayRequest = parameterReplayRequest;
    }

    public boolean hasParameterRequest() {
        return parameterReplayRequest != null;
    }

    public void clearParameterRequest() {
        this.parameterReplayRequest = null;
    }

    public void setAutostart(boolean autostart) {
        this.autostart = autostart;
    }

    public CommandHistoryReplayRequest getCommandHistoryRequest() {
        return commandHistoryReplayRequest == null ? CommandHistoryReplayRequest.getDefaultInstance()
                : commandHistoryReplayRequest;
    }

    public void setCommandHistoryRequest(CommandHistoryReplayRequest commandHistoryReplayRequest) {
        this.commandHistoryReplayRequest = commandHistoryReplayRequest;
    }

    public EventReplayRequest getEventRequest() {
        return eventReplayRequest == null ? EventReplayRequest.getDefaultInstance() : eventReplayRequest;
    }

    public void setEventRequest(EventReplayRequest eventReplayRequest) {
        this.eventReplayRequest = eventReplayRequest;
    }

    public boolean isReplayAll() {
        // As described in yamcs.proto, by default everything is replayed unless
        // at least one filter is specified.
        return ppRequest == null && packetReplayRequest == null && parameterReplayRequest == null
                && commandHistoryReplayRequest == null && eventReplayRequest == null;
    }

    public boolean isReplayAllParameters() {
        return isReplayAll() || (parameterReplayRequest != null && parameterReplayRequest.getNameFilterCount() == 0);
    }
}
