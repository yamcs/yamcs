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
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.Mdb.AlgorithmInfo;
import org.yamcs.protobuf.Mdb.ChangeAlgorithmRequest;
import org.yamcs.protobuf.Mdb.ChangeParameterRequest;
import org.yamcs.protobuf.Mdb.ParameterTypeInfo;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ProcessorData;
import org.yamcs.xtceproc.XtceDbFactory;

public class MdbRestHandler extends RestHandler {

    private static final Log log = new Log(MdbRestHandler.class);

    @Route(path = "/api/mdb/{instance}/{processor}/parameters/{name*}", method = { "PATCH", "PUT", "POST" })
    public void setParameterCalibrators(RestRequest req) throws HttpException {
        checkSystemPrivilege(req.getUser(), SystemPrivilege.ChangeMissionDatabase);

        Processor processor = verifyProcessor(req.getRouteParam("instance"), req.getRouteParam("processor"));
        XtceDb xtcedb = XtceDbFactory.getInstance(processor.getInstance());
        Parameter p = verifyParameter(req.getUser(), xtcedb, req.getRouteParam("name"));
        ChangeParameterRequest cpr = req.bodyAsMessage(ChangeParameterRequest.newBuilder()).build();
        ProcessorData pdata = processor.getProcessorData();
        ParameterType origParamType = p.getParameterType();

        switch (cpr.getAction()) {
        case RESET:
            pdata.clearParameterOverrides(p);
            break;
        case RESET_CALIBRATORS:
            pdata.clearParameterCalibratorOverrides(p);
            break;
        case SET_CALIBRATORS:
            verifyNumericParameter(p);
            if (cpr.hasDefaultCalibrator()) {
                pdata.setDefaultCalibrator(p, toCalibrator(cpr.getDefaultCalibrator()));
            }
            pdata.setContextCalibratorList(p,
                    toContextCalibratorList(xtcedb, p.getSubsystemName(), cpr.getContextCalibratorList()));
            break;
        case SET_DEFAULT_CALIBRATOR:
            verifyNumericParameter(p);
            if (cpr.hasDefaultCalibrator()) {
                pdata.setDefaultCalibrator(p, toCalibrator(cpr.getDefaultCalibrator()));
            } else {
                pdata.removeDefaultCalibrator(p);
            }
            break;
        case RESET_ALARMS:
            pdata.clearParameterAlarmOverrides(p);
            break;
        case SET_DEFAULT_ALARMS:
            if (!cpr.hasDefaultAlarm()) {
                pdata.removeDefaultAlarm(p);
            } else {
                if (origParamType instanceof NumericParameterType) {
                    pdata.setDefaultNumericAlarm(p, toNumericAlarm(cpr.getDefaultAlarm()));
                } else if (origParamType instanceof EnumeratedParameterType) {
                    pdata.setDefaultEnumerationAlarm(p, toEnumerationAlarm(cpr.getDefaultAlarm()));
                } else {
                    throw new BadRequestException("Can only set alarms on numeric or enumerated parameters");
                }
            }
            break;
        case SET_ALARMS:
            if (origParamType instanceof NumericParameterType) {
                if (cpr.hasDefaultAlarm()) {
                    pdata.setDefaultNumericAlarm(p, toNumericAlarm(cpr.getDefaultAlarm()));
                }
                pdata.setNumericContextAlarm(p,
                        toNumericContextAlarm(xtcedb, p.getSubsystemName(), cpr.getContextAlarmList()));
            } else if (origParamType instanceof EnumeratedParameterType) {
                if (cpr.hasDefaultAlarm()) {
                    pdata.setDefaultEnumerationAlarm(p, toEnumerationAlarm(cpr.getDefaultAlarm()));
                }
                pdata.setEnumerationContextAlarm(p,
                        toEnumerationContextAlarm(xtcedb, p.getSubsystemName(), cpr.getContextAlarmList()));
            } else {
                throw new BadRequestException("Can only set alarms on numeric or enumerated parameters");
            }
            break;
        default:
            throw new BadRequestException("Unknown action " + cpr.getAction());

        }
        ParameterType ptype = pdata.getParameterType(p);
        ParameterTypeInfo pinfo = XtceToGpbAssembler.toParameterTypeInfo(ptype, DetailLevel.FULL);
        completeOK(req, pinfo);
    }

    @Route(path = "/api/mdb/{instance}/{processor}/algorithms/{name*}", method = { "PATCH", "PUT", "POST" })
    public void setAlgorithm(RestRequest req) throws HttpException {
        checkSystemPrivilege(req.getUser(), SystemPrivilege.ChangeMissionDatabase);

        Processor processor = verifyProcessor(req.getRouteParam("instance"), req.getRouteParam("processor"));
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
        Algorithm a = verifyAlgorithm(xtcedb, req.getRouteParam("name"));
        if (!(a instanceof CustomAlgorithm)) {
            throw new BadRequestException("Can only patch CustomAlgorithm instances");
        }
        CustomAlgorithm calg = (CustomAlgorithm) a;
        ChangeAlgorithmRequest car = req.bodyAsMessage(ChangeAlgorithmRequest.newBuilder()).build();
        log.debug("received ChangeAlgorithmRequest {}", car);
        switch (car.getAction()) {
        case RESET:
            algMng.clearAlgorithmOverride(calg);
            break;
        case SET:
            if (!car.hasAlgorithm()) {
                throw new BadRequestException("No algorithm info provided");
            }
            AlgorithmInfo ai = car.getAlgorithm();
            if (!ai.hasText()) {
                throw new BadRequestException("No algorithm text provided");
            }
            try {
                log.debug("Setting text for algorithm {} to {}", calg.getQualifiedName(), ai.getText());
                algMng.setAlgorithmText(calg, ai.getText());
            } catch (Exception e) {
                throw new BadRequestException(e.getMessage());
            }
            break;
        default:
            throw new BadRequestException("Unknown action " + car.getAction());
        }

        completeOK(req);
    }

    private static void verifyNumericParameter(Parameter p) throws BadRequestException {
        ParameterType ptype = p.getParameterType();
        if (!(ptype instanceof NumericParameterType)) {
            throw new BadRequestException(
                    "Cannot set a calibrator on a non numeric parameter type (" + ptype.getTypeAsString() + ")");
        }
    }
}
