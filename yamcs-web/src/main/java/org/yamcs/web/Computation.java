package org.yamcs.web;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.japisoft.formula.Formula;
import com.japisoft.formula.FormulaFactory;
import com.japisoft.formula.Variant;
import com.japisoft.formula.node.EvaluateException;

import org.yamcs.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Value;

public class Computation {

    private static final Logger log = LoggerFactory.getLogger(Computation.class);

    private final String name;
    private final String expression;
    private final List<NamedObjectId> arguments;
    private final Formula formula;
    private final NamedObjectId id;
    
    public Computation(String name, String expression, List<NamedObjectId> arguments) {
        this.name=name;
        this.expression=expression;
        this.arguments=arguments;
        id=NamedObjectId.newBuilder().setName(name).build();
        FormulaFactory ff = FormulaFactory.getInstance();
        formula = ff.getNewFormula(expression);

    }

    public String getExpression() {
        return expression;
    }

    public String getName() {
        return name;
    }

    public org.yamcs.protobuf.Pvalue.ParameterValue evaluate(Map<NamedObjectId, ParameterValue> parameters) {
        boolean argUpdated=false;
        ParameterValue value=null;
        for (NamedObjectId noi :arguments) {
            value = parameters.get(noi);

            if (value == null) {
                continue;
                //  logger.warn("input value for parameter '" + param + "' was null");
            }
            
            Variant var = null;

            Value engValue = value.getEngValue();
            switch (engValue.getType()) {
            case BINARY:
                var = new Variant(engValue.getBinaryValue().toString());
                break;

            case DOUBLE:
                var = new Variant(engValue.getDoubleValue());
                break;

            case FLOAT:
                var = new Variant(engValue.getFloatValue());
                break;

            case SINT32:
                var = new Variant(engValue.getSint32Value());
                break;

            case UINT32:
                var = new Variant(engValue.getUint32Value());
                break;

            case STRING:
                var = new Variant(engValue.getStringValue());
                break;
            }

            formula.setSymbolValue(noi.getName(), var);
            argUpdated=true;
        }
        
        if(!argUpdated) return null;
        
        org.yamcs.protobuf.Pvalue.ParameterValue.Builder result = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder();
        result.setMonitoringResult(MonitoringResult.DISABLED);
        result.setId(id);
        try {
            Object val = formula.evaluate().getObjectResult();
            //System.out.println("got val: "+val+" of class "+val.getClass());
            Value.Builder ev=Value.newBuilder();
            if(val==null) { 
                //this happens for expressions that don't specify a return value, like
                //if (SOLAR_Solaces_uC1_Com_Stat=="NOT_OK")then "red"
                result.setAcquisitionStatus(AcquisitionStatus.INVALID);
                return null;
            } else if(val instanceof String) {
                ev.setType(Value.Type.STRING).setStringValue((String)val);
            } else if(val instanceof Integer) {
                ev.setType(Value.Type.SINT32).setSint32Value((Integer)val);
            } else if(val instanceof Double) {
                ev.setType(Value.Type.DOUBLE).setDoubleValue((Double)val);
            } else {
                log.warn("Unexpected computation result of type "+val.getClass()+": "+val+" parameterValue: "+value);
                return null;
            }
            result.setAcquisitionStatus(AcquisitionStatus.ACQUIRED);
            result.setEngValue(ev.build());
        } catch (EvaluateException e) {
            String msg=e.getMessage();
            if(msg!=null && msg.startsWith("No symbol resolver")) {
                //not an error: the computation depends on more parameters and one of them is not set
                //but perhaps we could find a less lousy way to check for this
                return null;
            }
            log.warn("evaluation exception ",e);
            return null;
        }

        return result.build();
    }
}
