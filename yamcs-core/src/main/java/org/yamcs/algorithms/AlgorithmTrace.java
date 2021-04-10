package org.yamcs.algorithms;

import java.util.ArrayDeque;
import java.util.List;

import org.yamcs.commanding.ArgumentValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.RawEngValue;

import com.google.protobuf.util.Timestamps;

public class AlgorithmTrace implements AlgorithmExecListener {
    public static final int MAX_RUNS = 500;
    public static final int MAX_LOGS = 500;

    ArrayDeque<org.yamcs.protobuf.AlgorithmTrace.Run> runs = new ArrayDeque<>();
    ArrayDeque<org.yamcs.protobuf.AlgorithmTrace.Log> logs = new ArrayDeque<>();

    public void addLog(String msg) {
        synchronized (logs) {
            org.yamcs.protobuf.AlgorithmTrace.Log log = org.yamcs.protobuf.AlgorithmTrace.Log.newBuilder()
                    .setTime(Timestamps.fromMillis(System.currentTimeMillis()))
                    .setMsg(msg)
                    .build();
            if (logs.size() >= MAX_LOGS) {
                logs.removeLast();
            }
            logs.addFirst(log);
        }
    }

    @Override
    public void algorithmRun(List<RawEngValue> inputValues, Object returnValue,
            List<ParameterValue> outputValues) {
        synchronized (runs) {
            if (runs.size() >= MAX_RUNS) {
                runs.removeLast();
            }
            org.yamcs.protobuf.AlgorithmTrace.Run.Builder runb = org.yamcs.protobuf.AlgorithmTrace.Run.newBuilder();
            if (inputValues != null) {
                for (RawEngValue rev : inputValues) {
                    if (rev instanceof ParameterValue) {
                        runb.addInputs(((ParameterValue) rev).toGpb());
                    } else {
                        runb.addInputs(((ArgumentValue) rev).toGpb());
                    }
                }
            }
            outputValues.forEach(pv -> runb.addOutputs(pv.toGpb()));
            runb.setTime(Timestamps.fromMillis(System.currentTimeMillis()));
            if (returnValue != null) {
                runb.setReturnValue(returnValue.toString());
            }
            runs.addFirst(runb.build());
        }
    }

    @Override
    public void algorithmError(List<RawEngValue> inputValues, String errorMsg) {
        synchronized (runs) {
            if (runs.size() >= MAX_RUNS) {
                runs.removeLast();
            }
            org.yamcs.protobuf.AlgorithmTrace.Run.Builder runb = org.yamcs.protobuf.AlgorithmTrace.Run.newBuilder();
            // inputValues.forEach(pv -> runb.addInputs(pv.toGpb()));
            runb.setError(errorMsg);
            runb.setTime(Timestamps.fromMillis(System.currentTimeMillis()));

            runs.addFirst(runb.build());
        }
    }

    public org.yamcs.protobuf.AlgorithmTrace toProto() {
        org.yamcs.protobuf.AlgorithmTrace.Builder trace = org.yamcs.protobuf.AlgorithmTrace.newBuilder();
        synchronized (runs) {
            trace.addAllRuns(runs);
        }
        synchronized (logs) {
            trace.addAllLogs(logs);
        }
        return trace.build();
    }

}
