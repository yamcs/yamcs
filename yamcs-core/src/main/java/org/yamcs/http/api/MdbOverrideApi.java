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
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.logging.Log;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.mdb.XtceDbFactory;
import org.yamcs.protobuf.AbstractMdbOverrideApi;
import org.yamcs.protobuf.AlgorithmTextOverride;
import org.yamcs.protobuf.GetAlgorithmOverridesRequest;
import org.yamcs.protobuf.GetAlgorithmOverridesResponse;
import org.yamcs.protobuf.ListMdbOverridesRequest;
import org.yamcs.protobuf.ListMdbOverridesResponse;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.protobuf.MdbOverrideInfo;
import org.yamcs.protobuf.MdbOverrideInfo.OverrideType;
import org.yamcs.protobuf.UpdateAlgorithmRequest;
import org.yamcs.protobuf.UpdateParameterRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.XtceDb;

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
                MdbOverrideInfo.Builder overrideb = MdbOverrideInfo.newBuilder()
                        .setType(OverrideType.ALGORITHM_TEXT)
                        .setAlgorithmTextOverride(toAlgorithmTextOverride(algorithm));

                responseb.addOverrides(overrideb);
            }
        }

        observer.complete(responseb.build());
    }

    @Override
    public void getAlgorithmOverrides(Context ctx, GetAlgorithmOverridesRequest request,
            Observer<GetAlgorithmOverridesResponse> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        XtceDb xtcedb = XtceDbFactory.getInstance(processor.getInstance());
        Algorithm algorithm = MdbApi.verifyAlgorithm(xtcedb, request.getName());

        GetAlgorithmOverridesResponse.Builder responseb = GetAlgorithmOverridesResponse.newBuilder();

        List<AlgorithmManager> l = processor.getServices(AlgorithmManager.class);
        if (l.size() == 1) {
            AlgorithmManager algorithmManager = l.get(0);
            CustomAlgorithm override = algorithmManager.getAlgorithmOverride(algorithm);
            if (override != null) {
                responseb.setTextOverride(toAlgorithmTextOverride(override));
            }
        }

        observer.complete(responseb.build());
    }

    private AlgorithmTextOverride toAlgorithmTextOverride(CustomAlgorithm algorithm) {
        AlgorithmTextOverride.Builder b = AlgorithmTextOverride.newBuilder()
                .setAlgorithm(algorithm.getQualifiedName())
                .setText(algorithm.getAlgorithmText());
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
        XtceDb xtcedb = XtceDbFactory.getInstance(processor.getInstance());
        Algorithm a = MdbApi.verifyAlgorithm(xtcedb, request.getName());
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
        XtceDb xtcedb = XtceDbFactory.getInstance(processor.getInstance());
        Parameter p = MdbApi.verifyParameter(ctx, xtcedb, request.getName());

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
                    toContextCalibratorList(xtcedb, p.getSubsystemName(), request.getContextCalibratorList()));
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
                        toNumericContextAlarm(xtcedb, p.getSubsystemName(), request.getContextAlarmList()));
            } else if (origParamType instanceof EnumeratedParameterType) {
                if (request.hasDefaultAlarm()) {
                    pdata.setDefaultEnumerationAlarm(p, toEnumerationAlarm(request.getDefaultAlarm()));
                }
                pdata.setEnumerationContextAlarm(p,
                        toEnumerationContextAlarm(xtcedb, p.getSubsystemName(), request.getContextAlarmList()));
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

    private static void verifyNumericParameter(Parameter p) throws BadRequestException {
        ParameterType ptype = p.getParameterType();
        if (!(ptype instanceof NumericParameterType)) {
            throw new BadRequestException(
                    "Cannot set a calibrator on a non numeric parameter type (" + ptype.getTypeAsString() + ")");
        }
    }
}
