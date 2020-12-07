package org.yamcs.xtce;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class TimeEpoch implements Serializable {
    private static final long serialVersionUID = 2L;

    public static enum CommonEpochs {
        TAI, J2000, UNIX, GPS
    };

    private final CommonEpochs epoch;
    private final String dateTime;

    public TimeEpoch(CommonEpochs epoch) {
        this.epoch = epoch;
        this.dateTime = null;
    }

    public TimeEpoch(String dateTime) {
        this.epoch = null;
        if (!validate(dateTime)) {
            throw new IllegalArgumentException("Invalid date time '" + dateTime + "'");
        }
        this.dateTime = dateTime;
    }

    public CommonEpochs getCommonEpoch() {
        return epoch;
    }

    public String getDateTime() {
        return dateTime;
    }

    private static boolean validate(String dateTime) {
        try {
            DateTimeFormatter.ISO_DATE_TIME.parse(dateTime);
            return true;
        } catch (DateTimeParseException e) {
        }
        try {
            DateTimeFormatter.ISO_DATE.parse(dateTime);
            return true;
        } catch (DateTimeParseException e) {
        }

        return false;
    }

    public String toString() {
        return epoch == null ? dateTime : epoch.toString();
    }
}
