package org.yamcs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.yamcs.tctm.TcUplinker;
import org.yamcs.tctm.TmPacketProvider;
import org.yamcs.xtce.MdbMappings;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;

/**
 * Periodically collects a set of system variables.
 * 
 * @author mache
 * 
 */
public class SystemVariablesManager extends AbstractExecutionThreadService
        implements Runnable, ParameterProvider {
    private Channel channel;
    private ParameterListener parameterRequestManager;
    ArrayList<SystemVariable> variableDefs = new ArrayList<SystemVariable>();
    ConcurrentLinkedQueue<ParameterValue> subscribedVariables = new ConcurrentLinkedQueue<ParameterValue>();

    SystemVariable sysvar_CDMCS_STATUS = new SystemVariable("CDMCS_STATUS");
    SystemVariable sysvar_CDMCS_FWLINK_STATUS = new SystemVariable("CDMCS_FWLINK_STATUS");
    SystemVariable sysvar_CDMCS_UPLINK_STATUS = new SystemVariable("CDMCS_UPLINK_STATUS");
    SystemVariable sysvar_CDMCS_DOWNLINK_STATUS = new SystemVariable("CDMCS_DOWNLINK_STATUS");
    SystemVariable sysvar_CDMCS_SIM_MODE = new SystemVariable("CDMCS_SIM_MODE");
    SystemVariable sysvar_CDMCS_TM_MODE = new SystemVariable("CDMCS_TM_MODE");
    SystemVariable sysvar_hk1300 = new SystemVariable("CGSHK_TRDB_STATUS", "1300");
    SystemVariable sysvar_hk1301 = new SystemVariable("CGSHK_CHDB_STATUS", "1301");
    SystemVariable sysvar_hk1302 = new SystemVariable("CGSHK_TES_EXPECTED", "1302");
    SystemVariable sysvar_hk1303 = new SystemVariable("CGSHK_TES_PRESENT", "1303");

    public SystemVariablesManager(ParameterListener parameterRequestManager,
            Channel channel) {
        this.parameterRequestManager = parameterRequestManager;
        this.channel = channel;
        variableDefs.add(sysvar_CDMCS_STATUS);
        variableDefs.add(sysvar_CDMCS_FWLINK_STATUS);
        variableDefs.add(sysvar_CDMCS_UPLINK_STATUS);
        variableDefs.add(sysvar_CDMCS_DOWNLINK_STATUS);
        variableDefs.add(sysvar_CDMCS_SIM_MODE);
        variableDefs.add(sysvar_CDMCS_TM_MODE);
        variableDefs.add(sysvar_hk1300);
        variableDefs.add(sysvar_hk1301);
        variableDefs.add(sysvar_hk1302);
        variableDefs.add(sysvar_hk1303);
    }

    public void collectVariables() {
        long d = TimeEncoding.currentInstant();
        sysvar_CDMCS_STATUS.update(d, "RUNNING");
        sysvar_CDMCS_SIM_MODE.update(d, "");
        sysvar_hk1300.update(d, "OK");
        sysvar_hk1301.update(d, "OK");
        sysvar_hk1302.update(d, "1");
        sysvar_hk1303.update(d, "1");

        TcUplinker tc = channel.getTcUplinker();
        if (tc != null) {
            sysvar_CDMCS_FWLINK_STATUS.update(d, tc.getFwLinkStatus());
            sysvar_CDMCS_UPLINK_STATUS.update(d, tc.getLinkStatus());
        } else {
            sysvar_CDMCS_FWLINK_STATUS.update(d, "NOK");
            sysvar_CDMCS_UPLINK_STATUS.update(d, "UNAVAIL");
        }
        TmPacketProvider tm = channel.getTmPacketProvider();
        sysvar_CDMCS_DOWNLINK_STATUS.update(d, tm.getLinkStatus());
        sysvar_CDMCS_TM_MODE.update(d, tm.getTmMode());
    }

    @Override
    public void startProviding(Parameter paramDef) {
        SystemVariable v = (SystemVariable) paramDef;
        subscribedVariables.add(v.value);
    }

    @Override
    public void startProvidingAll() {
        for(SystemVariable sv: variableDefs) {
            subscribedVariables.add(sv.value);
        }
    }

    @Override
    public void stopProviding(Parameter paramDef) {
        for (Iterator<ParameterValue> it = subscribedVariables.iterator(); it
                .hasNext();) {
            if (it.next().def == paramDef) {
                it.remove();
            }
        }
    }

    @Override
    public boolean canProvide(NamedObjectId paraId) {
        try {
            getParameter(paraId);
        } catch (InvalidIdentification e) {
            return false;
        }
        return true;
    }

    @Override
    public Parameter getParameter(NamedObjectId paraId)
            throws InvalidIdentification {
        for (Iterator<SystemVariable> it = variableDefs.iterator(); it.hasNext();) {
            SystemVariable p = (SystemVariable) it.next();
            String ns = paraId.getNamespace();
            if (MdbMappings.MDB_HKID.equals(ns)) {
                if ((p.hkId != null) && p.hkId.equals(paraId.getName()))
                    return p;
            } else if (MdbMappings.MDB_OPSNAME.equals(ns)) {
                if (p.getName().equals(paraId.getName()))
                    return p;
            } else {// all parameters are registered in either opsname or hkid
                    // namespaces
                throw new InvalidIdentification();
            }
        }
        throw new InvalidIdentification();
    }

    @Override
    public void run() {
        while (isRunning()) {
            if (subscribedVariables.size() > 0) {
                collectVariables();
                parameterRequestManager.update(subscribedVariables);
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setParameterListener(ParameterListener parameterRequestManager) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getDetailedStatus() {
        // TODO Auto-generated method stub
        return null;
    }
}

/**
 * let's not make a big fuss about it: store both the definition and the value
 * in the same object such that we create the values once at the beginning and
 * not when they are subscribed and we can just refer to them by name
 * 
 * @author nm
 * 
 */
class SystemVariable extends Parameter {
    String hkId = null;
    ParameterValue value = new ParameterValue(this, false);

    public SystemVariable(String opsName) {
        super(opsName);
        value.setBinaryValue(new byte[0]);// Initialize by default with an empty
                                          // string
    }

    public SystemVariable(String opsName, String hkId) {
        this(opsName);
        this.hkId = hkId;
    }

    public void update(long d, String s) {
        value.setAcquisitionTime(d);
        if (s != null) {
            value.setBinaryValue(s.getBytes());
        } else {
            value.setBinaryValue(new byte[0]);
        }
    }

    public String toString() {
        return "SysVar(opsname=" + getName() + ")";
    }
}