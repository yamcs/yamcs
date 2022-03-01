package org.yamcs.xtce;

import java.io.Serializable;

/**
 * XTCE: Significance provides some cautionary information about the potential consequence of each MetaCommand.
 * 
 * @author nm
 *
 */
public class Significance implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The XTCE aliases given to the Levels are from XTCE 1.2 and they correspond to ISO 14950 as well as the description found 
     * <a href="https://www.iso.org/obp/ui#iso:std:iso:14950:ed-1:v1:en:sec:4">here</a>.
     * <p>
     * In the future we will maybe adopt the XTCE names as the main names but that requires changes in the web interface as well as Yamcs Studio.
     * <p>
     * Note that the command privilege checking assumes there is an order in between the levels whereas the XTCE 1.2 does not
     * impose any ordering for user1 and user2.
     *
     */
    public enum Levels {
        /**
         * All commands which are not in a category below
         */
        NONE("normal"),
        /**
         * Mission specific
         */
        WATCH("user1"),
        /**
         * Mission specific
         */
        WARNING("user2"),
        /**
         * ISO 14490: telecommand that is not a critical telecommand but is essential to the success of the mission and,
         * if sent at the wrong time, could cause momentary loss of the mission
         */
        DISTRESS("vital"),
        /**
         * ISO 14490: telecommand that, if executed at the wrong time or in the wrong configuration, could cause
         * irreversible loss or damage for the mission (i.e. endanger the achievement of the primary mission objectives)
         */
        CRITICAL("critical"),
        /**
         * ISO 14490: telecommand that is not expected to be used for nominal or foreseeable contingency operations,
         * that is included for unforeseen contingency operations, and that could cause irreversible damage if executed
         * at the wrong time or in the wrong configuration
         */
        SEVERE("forbidden");

        private String xtceAlias;

        private Levels(String xtceAlias) {
            this.xtceAlias = xtceAlias;
        }

        public String xtceAlias() {
            return xtceAlias;
        }
        public boolean isMoreSevere(Levels other) {
            return ordinal() > other.ordinal();
        }

        public static Levels fromString(String value) {
            try {
                return Levels.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                // The value does not match any enumeration constant. See if it
                // matches an XTCE alias of one of the constants.
                for (Levels level : values()) {
                    if (level.xtceAlias.equalsIgnoreCase(value)) {
                        return level;
                    }
                }
                // No match. Propagate the original exception.
                throw e;
            }
        }
    }

    private String reasonForWarning;
    private Levels consequenceLevel;

    public Significance(Levels consequenceLevel, String reasonForWarning) {
        this.reasonForWarning = reasonForWarning;
        this.consequenceLevel = consequenceLevel;
    }

    public String getReasonForWarning() {
        return reasonForWarning;
    }

    public Levels getConsequenceLevel() {
        return consequenceLevel;
    }

    @Override
    public String toString() {
        return consequenceLevel + "(" + reasonForWarning + ")";
    }
}
