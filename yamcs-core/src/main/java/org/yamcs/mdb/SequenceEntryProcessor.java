package org.yamcs.mdb;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.AggregateValue;
import org.yamcs.parameter.ArrayValue;
import org.yamcs.parameter.ContainerParameterValue;
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
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;

public class SequenceEntryProcessor {
    static Logger log = LoggerFactory.getLogger(SequenceEntryProcessor.class.getName());
    ContainerProcessingContext pcontext;

    SequenceEntryProcessor(ContainerProcessingContext pcontext) {
        this.pcontext = pcontext;
    }

    public void extract(SequenceEntry se) {
        pcontext.currentEntry = se;
        if (se instanceof ArrayParameterEntry) {
            extractArrayParameterEntry((ArrayParameterEntry) se);
        } else if (se instanceof ParameterEntry) {
            extractParameterEntry((ParameterEntry) se);
        } else if (se instanceof ContainerEntry) {
            extractContainerEntry((ContainerEntry) se);
        } else if (se instanceof IndirectParameterRefEntry) {
            extractIndirectParameterRefEntry((IndirectParameterRefEntry) se);
        } else {
            throw new UnsupportedOperationException(
                    "processing entry of class " + se.getClass() + " not implemented");
        }
        pcontext.currentEntry = null;
    }

    private void extractContainerEntry(ContainerEntry ce) {
        BitBuffer buf = pcontext.buffer;
        if (buf.getPosition() % 8 != 0) {
            log.warn("Container Entry that doesn't start at byte boundary is not supported. "
                    + "{} is supposed to start at bit {}", ce, buf.getPosition());
            return;
        }
        if (buf.getPosition() > buf.sizeInBits()) {
            log.warn("Container Entry that doesn't fit in the buffer: {} is supposed to start at bit {}"
                    + " while the packet buffer has capacity {} bits", ce, buf.getPosition(), buf.sizeInBits());
            return;
        }
        SequenceContainer refContainer = ce.getRefContainer();
        while (refContainer.getBaseContainer() != null) {
            // TODO: this is weird, we go up the hierarchy to find the root but maybe the inheritance conditions don't
            // match.
            refContainer = refContainer.getBaseContainer();
        }

        SubscribedContainer subsribedContainer = pcontext.subscription.getSubscribedContainer(refContainer);
        if (subsribedContainer != null) {
            BitBuffer buf1 = buf.slice();

            ContainerProcessingContext cpc1 = new ContainerProcessingContext(pcontext.proccessorData, buf1,
                    pcontext.result, pcontext.subscription, pcontext.options, false);
            if (!pcontext.options.resultIncludesSubcontainers) {
                cpc1.provideContainerResult = false;
            }

            cpc1.sequenceContainerProcessor.extract(subsribedContainer);
            if (refContainer.getSizeInBits() < 0) {
                buf.setPosition(buf.getPosition() + buf1.getPosition());
            } else {
                buf.setPosition(buf.getPosition() + refContainer.getSizeInBits());
            }
        }
    }

    private ContainerParameterValue extractParameter(Parameter param) {
        ContainerProcessingResult result = pcontext.result;

        ParameterType ptype = param.getParameterType();
        if (ptype == null) {
            throw new XtceProcessingException(
                    "Encountered entry for parameter '" + param.getName() + " without a type");
        }
        ContainerParameterValue pv = new ContainerParameterValue(param,
                pcontext.buffer.offset(), pcontext.buffer.getPosition());
        int startPosition = pcontext.buffer.getPosition();

        Value rv = extract(ptype);
        if (rv == null) {
            // A dynamically sized array could have zero length.
            // Currently this is represented has having no value.
            if (ptype instanceof ArrayParameterType) {
                return null;
            }

            pv.setAcquisitionStatus(AcquisitionStatus.INVALID);
        } else {
            pv.setRawValue(rv);
        }
        pv.setBitSize(pcontext.buffer.getPosition() - startPosition);

        pcontext.proccessorData.parameterTypeProcessor.calibrate(result, pv);

        pv.setAcquisitionTime(result.acquisitionTime);
        pv.setGenerationTime(result.generationTime);
        pv.setExpireMillis(result.expireMillis);
        return pv;
    }

    private void extractParameterEntry(ParameterEntry pe) {
        ContainerParameterValue pv = extractParameter(pe.getParameter());
        if (pv != null) {
            pv.setSequenceEntry(pe);
            pcontext.result.addTmParam(pv);
        }
    }

    private void extractArrayParameterEntry(ArrayParameterEntry pe) {
        List<IntegerValue> size = pe.getSize();
        if (size == null) {
            size = pe.getSize();
        }
        int offset = pcontext.buffer.getPosition();
        var aptype = (ArrayParameterType) pe.getParameter().getParameterType();
        ArrayValue rv = extractArray(aptype, size);

        ContainerParameterValue pv = new ContainerParameterValue(pe.getParameter(),
                pcontext.buffer.offset(), offset);
        pv.setRawValue(rv);
        pv.setBitSize(pcontext.buffer.getPosition() - offset);
        if (rv.isEmpty()) {
            var engValueType = aptype.getElementType().getValueType();
            if (rv.getElementType() == engValueType) {
                pv.setEngValue(rv);
            } else {
                pv.setEngValue(new ArrayValue(rv.getDimensions(), engValueType));
            }
        } else {
            pcontext.proccessorData.parameterTypeProcessor.calibrate(pcontext.result, pv);
        }

        pv.setAcquisitionTime(pcontext.result.acquisitionTime);
        pv.setGenerationTime(pcontext.result.generationTime);
        pv.setExpireMillis(pcontext.result.expireMillis);
        pv.setSequenceEntry(pe);

        pcontext.result.addTmParam(pv);
    }

    private ArrayValue extractArray(ArrayParameterType aptype, List<IntegerValue> size) {
        int[] isize = new int[size.size()];
        int max = pcontext.options.getMaxArraySize();
        ParameterType elementType = (ParameterType) aptype.getElementType();

        for (int i = 0; i < size.size(); i++) {
            IntegerValue iv = size.get(i);
            long ds = pcontext.getIntegerValue(iv);
            if (ds == 0) { // zero size array
                org.yamcs.protobuf.Yamcs.Value.Type rawValueType;
                if (elementType instanceof AggregateDataType) {
                    rawValueType = org.yamcs.protobuf.Yamcs.Value.Type.AGGREGATE;
                } else if (elementType instanceof ArrayDataType) {
                    rawValueType = org.yamcs.protobuf.Yamcs.Value.Type.ARRAY;
                } else {
                    var encoding = elementType.getEncoding();
                    if (encoding == null) {
                        throw new XtceProcessingException(
                                "Encountered array parameter element'" + elementType.getName()
                                        + " without an encoding");
                    }
                    rawValueType = DataEncodingUtils.rawValueType(encoding);
                }
                return new ArrayValue(isize, rawValueType);
            } else if (ds < 0) {
                throw new XtceProcessingException(
                        "Negative array size " + ds + " encountered when processing array " + aptype.getName());
            } else if (ds > max) {
                throw new XtceProcessingException("Size of one dimension of array " + aptype.getName()
                        + " exceeds the max allowed: " + ds + " > " + max);
            }
            isize[i] = (int) ds;
        }

        int ts = ArrayValue.flatSize(isize);

        if (ts > max) {
            throw new XtceProcessingException("Resulted size of the array " + aptype.getName()
                    + " exceeds the max allowed: " + ts + " > " + max);
        }
        Value rv0 = extract(elementType);

        ArrayValue rv = new ArrayValue(isize, rv0.getType());
        rv.setElementValue(0, rv0);
        for (int i = 1; i < ts; i++) {
            rv0 = extract(elementType);
            rv.setElementValue(i, rv0);
        }
        return rv;
    }

    private void extractIndirectParameterRefEntry(IndirectParameterRefEntry se) {
        ParameterInstanceRef pir = se.getParameterRef();
        Value v = pcontext.getValue(pir);
        if (v == null) {
            throw new XtceProcessingException("Cannot determine the value of " + pir
                    + " necessary to extract the indirect parameter ref entry");
        }
        Mdb mdb = pcontext.getMdb();
        String nameSpace = se.getAliasNameSpace();
        String name = v.toString();
        log.trace("Looking up parameter with name/alias {} in namespace {}", name, nameSpace);

        Parameter p = nameSpace == null ? mdb.getParameter(name) : mdb.getParameter(nameSpace, name);
        if (p == null) {
            if (nameSpace == null) {
                throw new XtceProcessingException("Cannot find a parameter with FQN" + name);
            } else {
                throw new XtceProcessingException(
                        "Cannot find a parameter with name '" + name + "' in nameSpace '" + nameSpace + "'");
            }
        }
        ContainerParameterValue pv = extractParameter(p);
        if (pv != null) {
            pv.setSequenceEntry(se);
            pcontext.result.addTmParam(pv);
        }
    }

    private Value extract(ParameterType ptype) {
        if (ptype instanceof BaseDataType) {
            return extractBaseDataType((BaseDataType) ptype);
        } else if (ptype instanceof AggregateDataType) {
            return extractAggregateDataType((AggregateDataType) ptype);
        } else if (ptype instanceof ArrayParameterType) {
            ArrayParameterType aptype = (ArrayParameterType) ptype;
            return extractArray(aptype, aptype.getSize());
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
            result.setMemberValue(m.getName(), v);
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
        return pcontext.dataEncodingProcessor.extractRaw(encoding, pcontext);
    }
}
