package org.yamcs.xtceproc;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.BitBuffer;
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.ArrayDataType;
import org.yamcs.xtce.ArrayParameterEntry;
import org.yamcs.xtce.ArrayParameterType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.IndirectParameterRefEntry;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.XtceDb;

public class SequenceEntryProcessor {
    static Logger log = LoggerFactory.getLogger(SequenceEntryProcessor.class.getName());
    ContainerProcessingContext pcontext;
    static final int MAX_ARRAY_SIZE = 10000;

    SequenceEntryProcessor(ContainerProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    public void extract(SequenceEntry se) {
        if (se instanceof ParameterEntry) {
            extractParameterEntry((ParameterEntry) se);
        } else if (se instanceof ContainerEntry) {
            extractContainerEntry((ContainerEntry) se);
        } else if (se instanceof ArrayParameterEntry) {
            extractArrayParameterEntry((ArrayParameterEntry) se);
        } else if (se instanceof IndirectParameterRefEntry) {
            extractIndirectParameterRefEntry((IndirectParameterRefEntry) se);
        } else {
            throw new UnsupportedOperationException(
                    "processing entry of class " + se.getClass() + " not implemented");
        }
    }

    private void extractContainerEntry(ContainerEntry ce) {
        BitBuffer buf = pcontext.buffer;
        if (buf.getPosition() % 8 != 0)
            log.warn(
                    "Container Entry that doesn't start at byte boundary is not supported.{} is supposed to start at bit {}",
                    ce, buf.getPosition());
        if (buf.getPosition() > buf.sizeInBits()) {
            log.warn("Container Entry that doesn't fit in the buffer: {} is supposed to start at bit {}"
                    + " while the packet buffer has capacity {} bits", ce, buf.getPosition(), buf.sizeInBits());
            return;
        }
        BitBuffer buf1 = buf.slice();
        ContainerProcessingContext cpc1 = new ContainerProcessingContext(pcontext.pdata, buf1, pcontext.result,
                pcontext.subscription, pcontext.options);
        cpc1.sequenceContainerProcessor.extract(ce.getRefContainer());
        if (ce.getRefContainer().getSizeInBits() < 0)
            buf.setPosition(buf.getPosition() + buf1.getPosition());
        else
            buf.setPosition(buf.getPosition() + ce.getRefContainer().getSizeInBits());
    }

    private ParameterValue extractParameter(Parameter param) {
        ParameterType ptype = param.getParameterType();
        if (ptype == null) {
            throw new XtceProcessingException(
                    "Encountered entry for parameter '" + param.getName() + " without a type");
        }
        ParameterValue pv = new ParameterValue(param);
        int offset = pcontext.buffer.getPosition();
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset + offset);

        Value rv = extract(ptype);
        if (rv == null) {
            pv.setAcquisitionStatus(AcquisitionStatus.INVALID);
        } else {
            pv.setRawValue(rv);
        }
        pv.setBitSize(pcontext.buffer.getPosition() - offset);

        pcontext.pdata.parameterTypeProcessor.calibrate(pcontext, pv);

        pv.setAcquisitionTime(pcontext.result.acquisitionTime);
        pv.setGenerationTime(pcontext.result.generationTime);
        pv.setExpireMillis(pcontext.result.expireMillis);
        return pv;
    }

    private void extractParameterEntry(ParameterEntry pe) {
        ParameterValue pv = extractParameter(pe.getParameter());
        pv.setSequenceEntry(pe);
        pcontext.result.params.add(pv);
    }

    private void extractArrayParameterEntry(ArrayParameterEntry pe) {
        List<IntegerValue> size = pe.getSize();
        int[] isize = new int[size.size()];
        ValueProcessor valueproc = pcontext.valueProcessor;
        for (int i = 0; i < size.size(); i++) {
            IntegerValue iv = size.get(i);
            Long l = valueproc.getValue(iv);
            if (l == null) {
                throw new XtceProcessingException("Cannot compute value of " + iv
                        + " necessary to determine the size of the array " + pe.getParameter());
            }
            int ds = l.intValue();
            if (ds == 0) { // zero size array, just skip over it
                return;
            }
            isize[i] = ds;
        }
        int ts = ArrayValue.flatSize(isize);
        if (ts > MAX_ARRAY_SIZE) {
            throw new XtceProcessingException("Resulted size of the array " + pe.getParameter()
                    + " exceeds the max allowed: " + ts + " > " + MAX_ARRAY_SIZE);
        }
        ArrayParameterType aptype = (ArrayParameterType) pe.getParameter().getParameterType();
        ParameterType elementType = (ParameterType) aptype.getElementType();
        int offset = pcontext.buffer.getPosition();
        Value rv1 = extract(elementType);

        ArrayValue rv = new ArrayValue(isize, rv1.getType());
        rv.setElementValue(0, rv1);
        for (int i = 1; i < ts; i++) {
            rv1 = extract(elementType);
            rv.setElementValue(i, rv1);
        }

        ParameterValue pv = new ParameterValue(pe.getParameter());
        pv.setRawValue(rv);
        pv.setAbsoluteBitOffset(pcontext.containerAbsoluteByteOffset + offset);
        pv.setBitSize(pcontext.buffer.getPosition() - offset);

        pcontext.pdata.parameterTypeProcessor.calibrate(pcontext, pv);

        pv.setAcquisitionTime(pcontext.result.acquisitionTime);
        pv.setGenerationTime(pcontext.result.generationTime);
        pv.setExpireMillis(pcontext.result.expireMillis);
        pv.setSequenceEntry(pe);

        pcontext.result.params.add(pv);
    }

    private void extractIndirectParameterRefEntry(IndirectParameterRefEntry se) {
        ParameterInstanceRef pir = se.getParameterRef();
        Value v = pcontext.getValue(pir);
        if (v == null) {
            throw new XtceProcessingException("Cannot determine the value of " + pir
                    + " necessary to extract the indirect parameter ref entry");
        }
        XtceDb db = pcontext.getXtceDb();
        String nameSpace = se.getAliasNameSpace();
        String name = v.toString();
        log.trace("Looking up parameter with name/alias {} in namespace {}", name, nameSpace);

        Parameter p = nameSpace == null ? db.getParameter(name) : db.getParameter(nameSpace, name);
        if (p == null) {
            if (nameSpace == null) {
                throw new XtceProcessingException("Cannot find a parameter with FQN" + name);
            } else {
                throw new XtceProcessingException(
                        "Cannot find a parameter with name '" + name + "' in nameSpace '" + nameSpace + "'");
            }
        }
        ParameterValue pv = extractParameter(p);
        pv.setSequenceEntry(se);
        pcontext.result.params.add(pv);
    }

    private Value extract(ParameterType ptype) {
        if (ptype instanceof BaseDataType) {
            return extractBaseDataType((BaseDataType) ptype);
        } else if (ptype instanceof AggregateDataType) {
            return extractAggregateDataType((AggregateDataType) ptype);
        } else if (ptype instanceof ArrayDataType) {
            throw new IllegalStateException(
                    ptype.getName() + ": array parameter can only be referenced inside an ArrayParameterEntry");
        } else {
            throw new IllegalStateException("Unknonwn parameter type " + ptype.getClass());
        }
    }

    private Value extractAggregateDataType(AggregateDataType ptype) {
        AggregateValue result = new AggregateValue(ptype.getMemberNames());

        for (Member m : ptype.getMemberList()) {
            ParameterType mptype = (ParameterType) m.getType();
            if (mptype == null) {
                throw new XtceProcessingException("Encountered entry for aggregate parameter member'"
                        + ptype.getName() + "/" + m.getName() + " without a type");
            }

            Value v = extract(mptype);
            result.setValue(m.getName(), v);
        }
        return result;
    }

    private Value extractBaseDataType(BaseDataType ptype) {
        DataEncoding encoding = ptype.getEncoding();
        if (encoding == null) {
            throw new XtceProcessingException(
                    "Encountered parameter entry with a parameter type '" + ptype.getName()
                            + " without an encoding");
        }
        return pcontext.dataEncodingProcessor.extractRaw(encoding);
    }
}
