package org.yamcs.archive;

import org.yamcs.protobuf.Yamcs.CommandHistoryReplayRequest;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.EventReplayRequest;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.PpReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.utils.TimeEncoding;

public class ReplayOptions {
    long start = TimeEncoding.INVALID_INSTANT;
    long stop = TimeEncoding.INVALID_INSTANT;
    EndAction endAction;
    ReplaySpeed speed;
    boolean reverse;

    private PpReplayRequest ppRequest;
    private PacketReplayRequest packetReplayRequest;
    private ParameterReplayRequest parameterReplayRequest;
    private CommandHistoryReplayRequest commandHistoryReplayRequest;
    private EventReplayRequest eventReplayRequest;

    public ReplayOptions(ReplayRequest protoRequest) {
        if (protoRequest.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(protoRequest.getStart());
        }
        if (protoRequest.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(protoRequest.getStop());
        }
        endAction = protoRequest.getEndAction();
        if (protoRequest.hasSpeed()) {
            speed = protoRequest.getSpeed();
        } else {
            speed = ReplaySpeed.newBuilder().setType(ReplaySpeedType.REALTIME).setParam(1).build();
        }
        reverse = protoRequest.hasReverse() && protoRequest.getReverse();

        if (protoRequest.hasPacketRequest()) {
            this.packetReplayRequest = protoRequest.getPacketRequest();
        }

        if (protoRequest.hasPpRequest()) {
            this.ppRequest = protoRequest.getPpRequest();
        }
    }

    public ReplayOptions(long start, long stop) {
        this.start = start;
        this.stop = stop;
    }

    public ReplayOptions() {
    }

    public ReplayOptions(ReplayOptions other) {
        this.start = other.start;
        this.stop = other.stop;
        this.speed = other.speed;
        this.endAction = other.endAction;
        this.reverse = other.reverse;

        this.packetReplayRequest = other.packetReplayRequest;
        this.ppRequest = other.ppRequest;
        this.parameterReplayRequest = other.parameterReplayRequest;
        this.commandHistoryReplayRequest = other.commandHistoryReplayRequest;
        this.eventReplayRequest = other.eventReplayRequest;
    }

    public static ReplayOptions getAfapReplay(long start, long stop) {
        ReplayOptions repl = new ReplayOptions(start, stop);
        repl.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP).build());
        repl.setEndAction(EndAction.QUIT);
        return repl;
    }

    public static ReplayOptions getAfapReplay() {
        ReplayOptions repl = new ReplayOptions();
        repl.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP).build());
        repl.setEndAction(EndAction.QUIT);
        return repl;
    }

    public void setSpeed(ReplaySpeed speed) {
        this.speed = speed;
    }

    public void setStart(long start) {
        this.start = start;

    }

    public ReplaySpeed getSpeed() {
        return speed;
    }

    public boolean isReverse() {
        return reverse;
    }

    public ReplayRequest toProtobuf() {
        ReplayRequest.Builder rr = ReplayRequest.newBuilder();
        if (start != TimeEncoding.INVALID_INSTANT) {
            rr.setStart(TimeEncoding.toProtobufTimestamp(start));
        }
        if (stop != TimeEncoding.INVALID_INSTANT) {
            rr.setStart(TimeEncoding.toProtobufTimestamp(stop));
        }
        rr.setSpeed(speed);
        rr.setEndAction(endAction);
        rr.setReverse(reverse);
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

    public long getStart() {
        return start;
    }

    public long getStop() {
        return stop;
    }

    public boolean hasStart() {
        return start != TimeEncoding.INVALID_INSTANT;
    }

    public boolean hasStop() {
        return stop != TimeEncoding.INVALID_INSTANT;
    }

    public void setEndAction(EndAction endAction) {
        this.endAction = endAction;
    }

    public void setStop(long stop) {
        this.stop = stop;
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

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
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

}
