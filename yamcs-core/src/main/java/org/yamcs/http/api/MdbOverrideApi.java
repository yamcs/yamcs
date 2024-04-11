package org.yamcs.http.api;

import static org.yamcs.http.api.GbpToXtceAssembler.toCalibrator;
import static org.yamcs.http.api.GbpToXtceAssembler.toContextCalibratorList;
import static org.yamcs.http.api.GbpToXtceAssembler.toEnumerationAlarm;
import static org.yamcs.http.api.GbpToXtceAssembler.toEnumerationContextAlarm;
import static org.yamcs.http.api.GbpToXtceAssembler.toNumericAlarm;
import static org.yamcs.http.api.GbpToXtceAssembler.toNumericContextAlarm;

import java.util.List;

import org.yamcs.Processor;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.algorithms.AlgorithmTextListener;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.logging.Log;
import org.yamcs.mdb.ParameterTypeListener;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.protobuf.AbstractMdbOverrideApi;
import org.yamcs.protobuf.AlgorithmTextOverride;
import org.yamcs.protobuf.GetAlgorithmOverridesRequest;
import org.yamcs.protobuf.GetAlgorithmOverridesResponse;
import org.yamcs.protobuf.GetParameterOverrideRequest;
import org.yamcs.protobuf.ListMdbOverridesRequest;
import org.yamcs.protobuf.ListMdbOverridesResponse;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.MdbOverrideInfo;
import org.yamcs.protobuf.MdbOverrideInfo.OverrideType;
import org.yamcs.protobuf.ParameterOverride;
import org.yamcs.protobuf.SubscribeMdbChangesRequest;
import org.yamcs.protobuf.UpdateAlgorithmRequest;
import org.yamcs.protobuf.UpdateParameterRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.mdb.Mdb;

import com.google.protobuf.Empty;

public class MdbOverrideApi extends AbstractMdbOverrideApi<Context> {

    private static final Log log = new Log(MdbOverrideApi.class);

    @Override
    public void listMdbOverrides(Context ctx, ListMdbOverridesRequest request,
            Observer<ListMdbOverridesResponse> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());

        ListMdbOverridesResponse.Builder responseb = ListMdbOverridesResponse.newBuilder();

        List<AlgorithmManager> l = processor.getServices(AlgorithmManager.class);
        if (l.size() == 1) {
            AlgorithmManager algorithmManager = l.get(0);
            for (CustomAlgorithm algorithm : algorithmManager.getAlgorithmOverrides()) {
                var overrideb = MdbOverrideInfo.newBuilder()
                        .setType(OverrideType.ALGORITHM_TEXT)
                        .setAlgorithmTextOverride(toAlgorithmTextOverride(algorithm, algorithm.getAlgorithmText()));
                responseb.addOverrides(overrideb);
            }
        }

        ProcessorData pdata = processor.getProcessorData();
        for (var entry : pdata.getParameterTypeOverrides().entrySet()) {
            var overrideb = MdbOverrideInfo.newBuilder()
                    .setType(OverrideType.PARAMETER)
                    .setParameterOverride(toParameterOverride(entry.getKey(), entry.getValue()));
            responseb.addOverrides(overrideb);
        }

        observer.complete(responseb.build());
    }

    @Override
    public void getParameterOverride(Context ctx, GetParameterOverrideRequest request,
            Observer<ParameterOverride> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        Mdb mdb = MdbFactory.getInstance(processor.getInstance());
        Parameter parameter = MdbApi.verifyParameter(ctx, mdb, request.getName());

        ProcessorData pdata = processor.getProcessorData();

        var ptype = pdata.getParameterTypeOverride(parameter);
        if (ptype != null) {
            observer.complete(toParameterOverride(parameter, ptype));
        } else {
            observer.complete(ParameterOverride.getDefaultInstance());
        }
    }

    private static ParameterOverride toParameterOverride(Parameter parameter, ParameterType ptype) {
        var b = ParameterOverride.newBuilder()
                .setParameter(parameter.getQualifiedName());
        var info = XtceToGpbAssembler.toParameterTypeInfo(ptype, DetailLevel.FULL);

        if (info.getDataEncoding().hasDefaultCalibrator()) {
            b.setDefaultCalibrator(info.getDataEncoding().getDefaultCalibrator());
        }
        b.addAllContextCalibrators(info.getDataEncoding().getContextCalibratorList());

        if (info.hasDefaultAlarm()) {
            b.setDefaultAlarm(info.getDefaultAlarm());
        }
        b.addAllContextAlarms(info.getContextAlarmList());
        return b.build();
    }

    @Override
    public void getAlgorithmOverrides(Context ctx, GetAlgorithmOverridesRequest request,
            Observer<GetAlgorithmOverridesResponse> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        Mdb mdb = MdbFactory.getInstance(processor.getInstance());
        Algorithm algorithm = MdbApi.verifyAlgorithm(mdb, request.getName());

        GetAlgorithmOverridesResponse.Builder responseb = GetAlgorithmOverridesResponse.newBuilder();

        List<AlgorithmManager> l = processor.getServices(AlgorithmManager.class);
        if (l.size() == 1) {
            AlgorithmManager algorithmManager = l.get(0);
            CustomAlgorithm override = algorithmManager.getAlgorithmOverride(algorithm);
            if (override != null) {
                responseb.setTextOverride(toAlgorithmTextOverride(override, override.getAlgorithmText()));
            }
        }

        observer.complete(responseb.build());
    }

    private AlgorithmTextOverride toAlgorithmTextOverride(CustomAlgorithm algorithm, String algorithmText) {
        AlgorithmTextOverride.Builder b = AlgorithmTextOverride.newBuilder()
                .setAlgorithm(algorithm.getQualifiedName())
                .setText(algorithmText);
        return b.build();
    }

    @Override
    public void updateAlgorithm(Context ctx, UpdateAlgorithmRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ChangeMissionDatabase);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        List<AlgorithmManager> l = processor.getServices(AlgorithmManager.class);
        if (l.size() == 0) {
            throw new BadRequestException("No AlgorithmManager available for this processor");
        }
        if (l.size() > 1) {
            throw new BadRequestException(
                    "Cannot patch algorithm when a processor has more than 1 AlgorithmManager services");
        }
        AlgorithmManager algMng = l.get(0);
        Mdb mdb = MdbFactory.getInstance(processor.getInstance());
        Algorithm a = MdbApi.verifyAlgorithm(mdb, request.getName());
        if (!(a instanceof CustomAlgorithm)) {
            throw new BadRequestException("Can only patch CustomAlgorithm instances");
        }
        CustomAlgorithm calg = (CustomAlgorithm) a;

        switch (request.getAction()) {
        case RESET:
            algMng.clearAlgorithmOverride(calg);
            break;
        case SET:
            if (!request.hasAlgorithm()) {
                throw new BadRequestException("No algorithm info provided");
            }
            AlgorithmInfo ai = request.getAlgorithm();
            if (!ai.hasText()) {
                throw new BadRequestException("No algorithm text provided");
            }
            try {
                log.debug("Setting text for algorithm {} to {}", calg.getQualifiedName(), ai.getText());
                algMng.overrideAlgorithm(calg, ai.getText());
            } catch (Exception e) {
                throw new BadRequestException(e.getMessage());
            }
            break;
        default:
            throw new BadRequestException("Unknown action " + request.getAction());
        }

        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void updateParameter(Context ctx, UpdateParameterRequest request, Observer<ParameterTypeInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ChangeMissionDatabase);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        Mdb mdb = MdbFactory.getInstance(processor.getInstance());
        Parameter p = MdbApi.verifyParameter(ctx, mdb, request.getName());

        ProcessorData pdata = processor.getProcessorData();
        ParameterType origParamType = p.getParameterType();

        switch (request.getAction()) {
        case RESET:
            pdata.clearParameterOverrides(p);
            break;
        case RESET_CALIBRATORS:
            pdata.clearParameterCalibratorOverrides(p);
            break;
        case SET_CALIBRATORS:
            verifyNumericParameter(p);
            if (request.hasDefaultCalibrator()) {
                pdata.setDefaultCalibrator(p, toCalibrator(request.getDefaultCalibrator()));
            }
            pdata.setContextCalibratorList(p,
                    toContextCalibratorList(mdb, p.getSubsystemName(), request.getContextCalibratorList()));
            break;
        case SET_DEFAULT_CALIBRATOR:
            verifyNumericParameter(p);
            if (request.hasDefaultCalibrator()) {
                pdata.setDefaultCalibrator(p, toCalibrator(request.getDefaultCalibrator()));
            } else {
                pdata.removeDefaultCalibrator(p);
            }
            break;
        case RESET_ALARMS:
            pdata.clearParameterAlarmOverrides(p);
            break;
        case SET_DEFAULT_ALARMS:
            if (!request.hasDefaultAlarm()) {
                pdata.removeDefaultAlarm(p);
            } else {
                if (origParamType instanceof NumericParameterType) {
                    pdata.setDefaultNumericAlarm(p, toNumericAlarm(request.getDefaultAlarm()));
                } else if (origParamType instanceof EnumeratedParameterType) {
                    pdata.setDefaultEnumerationAlarm(p, toEnumerationAlarm(request.getDefaultAlarm()));
                } else {
                    throw new BadRequestException("Can only set alarms on numeric or enumerated parameters");
                }
            }
            break;
        case SET_ALARMS:
            if (origParamType instanceof NumericParameterType) {
                if (request.hasDefaultAlarm()) {
                    pdata.setDefaultNumericAlarm(p, toNumericAlarm(request.getDefaultAlarm()));
                }
                pdata.setNumericContextAlarm(p,
                        toNumericContextAlarm(mdb, p.getSubsystemName(), request.getContextAlarmList()));
            } else if (origParamType instanceof EnumeratedParameterType) {
                if (request.hasDefaultAlarm()) {
                    pdata.setDefaultEnumerationAlarm(p, toEnumerationAlarm(request.getDefaultAlarm()));
                }
                pdata.setEnumerationContextAlarm(p,
                        toEnumerationContextAlarm(mdb, p.getSubsystemName(), request.getContextAlarmList()));
            } else {
                throw new BadRequestException("Can only set alarms on numeric or enumerated parameters");
            }
            break;
        default:
            throw new BadRequestException("Unknown action " + request.getAction());

        }
        ParameterType ptype = pdata.getParameterType(p);
        ParameterTypeInfo pinfo = XtceToGpbAssembler.toParameterTypeInfo(ptype, DetailLevel.FULL);
        observer.complete(pinfo);
    }

    @Override
    public void subscribeMdbChanges(Context ctx, SubscribeMdbChangesRequest request,
            Observer<MdbOverrideInfo> observer) {
        var processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        var pdata = processor.getProcessorData();

        List<AlgorithmManager> l = processor.getServices(AlgorithmManager.class);
        AlgorithmManager algorithmManager = l.size() == 1 ? l.get(0) : null;

        var parameterTypeListener = (ParameterTypeListener) (parameter, ptype) -> {
            observer.next(MdbOverrideInfo.newBuilder()
                    .setType(OverrideType.PARAMETER)
                    .setParameterOverride(toParameterOverride(parameter, ptype))
                    .build());
        };

        var algorithmTextListener = (AlgorithmTextListener) (algorithm, text) -> {
            observer.next(MdbOverrideInfo.newBuilder()
                    .setType(OverrideType.ALGORITHM_TEXT)
                    .setAlgorithmTextOverride(toAlgorithmTextOverride(algorithm, text))
                    .build());
        };

        observer.setCancelHandler(() -> {
            pdata.removeParameterTypeListener(parameterTypeListener);
            if (algorithmManager != null) {
                algorithmManager.removeAlgorithmTextListener(algorithmTextListener);
            }
        });
        pdata.addParameterTypeListener(parameterTypeListener);
        if (algorithmManager != null) {
            algorithmManager.addAlgorithmTextListener(algorithmTextListener);
        }
    }

    private static void verifyNumericParameter(Parameter p) throws BadRequestException {
        ParameterType ptype = p.getParameterType();
        if (!(ptype instanceof NumericParameterType)) {
            throw new BadRequestException(
                    "Cannot set a calibrator on a non numeric parameter type (" + ptype.getTypeAsString() + ")");
        }
    }
}
