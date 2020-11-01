package org.yamcs.yarch;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TaiUtcConverter;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;

/**
 * Implements different schemes for partitioning by time.
 * It gives back a partition start/end and a directory where data shall be stored.
 * 
 * Currently the following are implemented:
 * YYYY
 * YYYY/DOY
 * YYYY/MM
 * 
 * @author nm
 *
 */
public abstract class TimePartitionSchema {
    /**
     * returns the directory where this instant shall be written.
     * This is likely to be expensive operation - since instant has to be converted into a calendar taking into accounts
     * leap seconds and all the rest.
     *
     * @param instant
     */
    public abstract TimePartitionInfo getPartitionInfo(long instant);

    /**
     * Parses a string of the shape "A/B/..." into a PartitionInfo.
     * It is used by the storage engines to parse the partitions from disk at startup.
     *
     * Returns null if the given string does not match the expected directory.
     */
    public abstract TimePartitionInfo parseDir(String dir);

    private String name;

    static public TimePartitionSchema getInstance(String schema) {
        TimePartitionSchema tps;
        if ("YYYY/DOY".equalsIgnoreCase(schema)) {
            tps = new YYYYDOY();
        } else if ("YYYY/MM".equalsIgnoreCase(schema)) {
            tps = new YYYYMM();
        } else if ("YYYY".equalsIgnoreCase(schema)) {
            tps = new YYYY();
        } else {
            throw new IllegalArgumentException("Invalid time partitioning schema '" + schema
                    + "'. Supported schemas are: YYYY/DOY, YYYY/MM and YYYY");
        }

        tps.name = schema;
        return tps;
    }

    static class YYYYDOY extends TimePartitionSchema {
        Pattern p = Pattern.compile("(\\d{4,})/(\\d{3})");

        @Override
        public TimePartitionInfo getPartitionInfo(long instant) {
            DateTimeComponents dtc = TimeEncoding.toUtc(instant);
            return getPartitionInfo(dtc.getYear(), dtc.getDoy());
        }

        @Override
        public TimePartitionInfo parseDir(String dir) {
            Matcher m = p.matcher(dir);
            if (m.matches()) {
                int year = Integer.parseInt(m.group(1));
                int doy = Integer.parseInt(m.group(2));
                return getPartitionInfo(year, doy);
            } else {
                return null;
            }
        }

        private TimePartitionInfo getPartitionInfo(int year, int doy) {
            TimePartitionInfo pinfo = new TimePartitionInfo();
            String start = String.format("%04d/%03dT00:00:00Z", year, doy);
            int endy = year;
            int endd = doy+1;
            
            if((endd==366 && !TaiUtcConverter.isLeap(year)) || endd==367) {
                endd=1;
                endy++;
            }
            
            String end = String.format("%04d/%03dT00:00:00Z", endy, endd);
            
            pinfo.setStart(TimeEncoding.parse(start));
            pinfo.setEnd(TimeEncoding.parse(end));
            pinfo.setDir(String.format("%04d/%03d", year, doy));
            return pinfo;
        }

    }

    static class YYYYMM extends TimePartitionSchema {
        Pattern p = Pattern.compile("(\\d{4,})/(\\d{2})");

        @Override
        public TimePartitionInfo getPartitionInfo(long instant) {
            DateTimeComponents dtc = TimeEncoding.toUtc(instant);
            return getPartitionInfo(dtc.getYear(), dtc.getMonth());
        }

        @Override
        public TimePartitionInfo parseDir(String dir) {
            Matcher m = p.matcher(dir);
            if (m.matches()) {
                int year = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                return getPartitionInfo(year, month);
            } else {
                return null;
            }
        }

        private TimePartitionInfo getPartitionInfo(int year, int month) {
            TimePartitionInfo pinfo = new TimePartitionInfo();

            String start = String.format("%04d-%02d-01T00:00:00Z", year, month);
            int endm = month+1;
            int endy = year;
            if (endm == 13) {
                endm = 1;
                endy++;
            }
            String end = String.format("%04d-%02d-01T00:00:00Z", endy, endm);
            pinfo.setStart(TimeEncoding.parse(start));
            pinfo.setEnd(TimeEncoding.parse(end));

            pinfo.setDir(String.format("%04d/%02d", year, month));
            return pinfo;
        }
    }

    static class YYYY extends TimePartitionSchema {
        Pattern p = Pattern.compile("(\\d{4,})");

        @Override
        public TimePartitionInfo getPartitionInfo(long instant) {
            DateTimeComponents dtc = TimeEncoding.toUtc(instant);
            return getPartitionInfo(dtc.getYear());
        }

        @Override
        public TimePartitionInfo parseDir(String dir) {
            Matcher m = p.matcher(dir);
            if (m.matches()) {
                int year = Integer.parseInt(m.group(1));
                return getPartitionInfo(year);
            } else {
                return null;
            }
        }

        private TimePartitionInfo getPartitionInfo(int year) {
            TimePartitionInfo pinfo = new TimePartitionInfo();
            String start = String.format("%04d-01-01T00:00:00Z", year);
            String end = String.format("%04d-01-01T00:00:00Z", year + 1);
            pinfo.setStart(TimeEncoding.parse(start));
            pinfo.setEnd(TimeEncoding.parse(end));
            pinfo.setDir(String.format("%04d", year));
            return pinfo;
        }
    }

    /**
     * name for the partitioning schema (YYYY/DOY, YYYY/MM, etc)
     * 
     * @return the name of the partitioning schema
     */
    public String getName() {
        return name;
    }

}
