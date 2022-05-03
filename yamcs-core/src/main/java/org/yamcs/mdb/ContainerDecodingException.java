package org.yamcs.mdb;

import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.IndirectParameterRefEntry;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.SequenceEntry;

public class ContainerDecodingException extends XtceProcessingException {
    ContainerProcessingContext pcontext;

    public ContainerDecodingException(String message) {
        super(message);
    }

    public ContainerDecodingException(ContainerProcessingContext pcontext, String message) {
        super(message);
        this.pcontext = pcontext;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Error processing container: ");
        SequenceEntry se = pcontext.currentEntry;
        if (se != null) {
            sb.append("while extracting ");
            if (se instanceof ContainerEntry) {
                ContainerEntry ce = (ContainerEntry) se;
                sb.append("container " + ce.getRefContainer().getQualifiedName());
            } else if (se instanceof ParameterEntry) {
                ParameterEntry pe = (ParameterEntry) se;
                sb.append("parameter " + pe.getParameter().getQualifiedName());
            } else if (se instanceof IndirectParameterRefEntry) {
                IndirectParameterRefEntry ipre = (IndirectParameterRefEntry) se;
                sb.append("indirect parameter " + ipre.getParameterRef());
            }
        }
        sb.append(" at bit position ")
                .append(pcontext.buffer.getPosition())
                .append(": ");

        sb.append(getMessage());
        return sb.toString();
    }

}
