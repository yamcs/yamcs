package org.yamcs.http.api;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.utils.parser.Filter;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.utils.parser.UnknownFieldException;
import org.yamcs.xtce.SequenceContainer;

public class ContainerFilter extends Filter<ContainerFilter.MatchTarget> {

    private static final String FIELD_NAME = "name";
    private static final String FIELD_LINK = "link";
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_SEQ_NUMBER = "seqNumber";

    private String lcName;
    private String lcLink;

    public ContainerFilter(String query) throws ParseException, UnknownFieldException {
        super(query);
        addStringField(FIELD_NAME, this::getName);
        addStringField(FIELD_LINK, this::getLink);
        addNumberField(FIELD_SIZE, this::getSize);
        addNumberField(FIELD_SEQ_NUMBER, this::getSeqNumber);
        parse();
    }

    @Override
    public void beforeItem(MatchTarget target) {
        // Preload lowercase variants to boost non-field text search
        // with multiple terms

        // Reset previous state
        lcName = null;
        lcLink = null;

        if (includesTextSearch()) {
            lcName = getName(target).toLowerCase();
            if (target.link != null) {
                lcLink = target.link.toLowerCase();
            }
        }
    }

    private String getName(MatchTarget target) {
        return target.container.getQualifiedName();
    }

    private String getLink(MatchTarget target) {
        return target.link;
    }

    private Number getSize(MatchTarget target) {
        return target.size;
    }

    private Number getSeqNumber(MatchTarget target) {
        return target.seqNumber;
    }

    @Override
    protected boolean matchesLiteral(MatchTarget target, String lowercaseLiteral) {
        return (lcName != null && lcName.contains(lowercaseLiteral))
                || (lcLink != null && lcLink.contains(lowercaseLiteral));
    }

    /**
     * A container match target, used in two modes:
     * <ul>
     * <li>discovery: a bare MDB container definition, used to decide which containers to subscribe to.
     * {@code link}/{@code size}/{@code seqNumber} are unknown at this stage (only known once a container has
     * actually been received), and resolve to {@code null}.</li>
     * <li>delivery: a live received container, used to decide whether to forward a message to a subscriber. All
     * fields are known.</li>
     * </ul>
     */
    public static final class MatchTarget {
        final SequenceContainer container;
        final String link;
        final Integer size;
        final Integer seqNumber;

        private MatchTarget(SequenceContainer container, String link, Integer size, Integer seqNumber) {
            this.container = container;
            this.link = link;
            this.size = size;
            this.seqNumber = seqNumber;
        }

        public static MatchTarget forDiscovery(SequenceContainer container) {
            return new MatchTarget(container, null, null, null);
        }

        public static MatchTarget forDelivery(String link, ContainerExtractionResult cer) {
            return new MatchTarget(cer.getContainer(), link, cer.getContainerContent().length, cer.getSeqCount());
        }
    }
}
