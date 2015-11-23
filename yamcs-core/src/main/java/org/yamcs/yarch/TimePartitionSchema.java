package org.yamcs.yarch;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TaiUtcConverter.DateTimeComponents;

/**
 * Implements different schemes for partitioning by time. 
 * It gives back a partition start/end and a directory where data shall be stored.
 *  
 * Currently the following are implemented:
 *   YYYY/DOY
 *   YYYY/MM
 * @author nm
 *
 */
public abstract class TimePartitionSchema {
    /**
     * returns the directory where this instant shall be written.
     * This is likely to be expensive operation - since instant has to be converted into a calendar taking into accounts leap seconds and all the rest.
     *
     * @param instant
     * @return
     */
    public abstract PartitionInfo getPartitionInfo(long instant);

    /**
     * Parses a string of the shape "A/B/..." into a PartitionInfo.
     * It is used by the storage engines to parse the partitions from disk at startup.
     *
     * Returns null if the given string does not match the expected directory.
     * @return
     */
    public abstract PartitionInfo parseDir(String dir);

    private String name;

    static public TimePartitionSchema getInstance(String schema) {
        TimePartitionSchema tps;
        if("YYYY/DOY".equalsIgnoreCase(schema)) {
            tps = new YYYYDOY();
        } else if ("YYYY/MM".equalsIgnoreCase(schema)) {
            tps = new YYYYMM();
        } else if ("YYYY".equalsIgnoreCase(schema)) {
            tps = new YYYY();
        } else {
            throw new IllegalArgumentException("Invalid time partitioning schema '"+schema+"'. Supported schemas are: YYYY/DOY, YYYY/MM and YYYY");
        }

        tps.name = schema;
        return tps;
    }



    public static class PartitionInfo {
        @Override
        public String toString() {
            return "PartitionInfo [dir=" + dir + ", partitionStart="
                    + partitionStart + ", partitionEnd=" + partitionEnd + "]";
        }
        public String dir;
        public long partitionStart;
        public long partitionEnd;
    }



    /**
     * name for the partitioning schema (YYYY/DOY, YYYY/MM, etc)
     * @return
     */
    public String getName() {
        return name;
    }




    static class YYYYDOY extends TimePartitionSchema {
        Pattern p = Pattern.compile("(\\d{4})/(\\d{3})");
        @Override
        public PartitionInfo getPartitionInfo(long instant) {
            DateTimeComponents dtc =TimeEncoding.toUtc(instant);
            return getPartitionInfo(dtc.year, dtc.doy);
        }

        @Override
        public PartitionInfo parseDir(String dir) {
            Matcher m = p.matcher(dir);
            if(m.matches()) {
                int year = Integer.parseInt(m.group(1));
                int doy = Integer.parseInt(m.group(2));
                return getPartitionInfo(year, doy);
            } else {
                return null;
            }
        }

        private PartitionInfo getPartitionInfo(int year, int doy) {
            PartitionInfo pinfo=new PartitionInfo();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.DAY_OF_YEAR, doy);
            pinfo.partitionStart = TimeEncoding.fromCalendar(cal);
            cal.add(Calendar.DAY_OF_YEAR, 1);
            pinfo.partitionEnd = TimeEncoding.fromCalendar(cal);
            pinfo.dir = String.format("%4d/%03d", year, doy);
            return pinfo;
        }

    }

    static class YYYYMM extends TimePartitionSchema {
        Pattern p = Pattern.compile("(\\d{4})/(\\d{2})");
        @Override
        public PartitionInfo getPartitionInfo(long instant) {
            DateTimeComponents dtc =TimeEncoding.toUtc(instant);
            return getPartitionInfo(dtc.year, dtc.month);
        }

        @Override
        public PartitionInfo parseDir(String dir) {
            Matcher m = p.matcher(dir);
            if(m.matches()) {
                int year = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                return getPartitionInfo(year, month);
            } else {
                return null;
            }
        }

        private PartitionInfo getPartitionInfo(int year, int month) {
            PartitionInfo pinfo=new PartitionInfo();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month-1);
            cal.set(Calendar.HOUR, 0);

            pinfo.partitionStart = TimeEncoding.fromCalendar(cal);
            cal.add(Calendar.MONTH, 1);
            pinfo.partitionEnd = TimeEncoding.fromCalendar(cal);
            pinfo.dir = String.format("%4d/%02d", year, month);
            return pinfo;
        }
    }

    static class YYYY extends TimePartitionSchema {
        Pattern p = Pattern.compile("(\\d{4})");
        @Override
        public PartitionInfo getPartitionInfo(long instant) {
            DateTimeComponents dtc =TimeEncoding.toUtc(instant);
            return getPartitionInfo(dtc.year);
        }

        @Override
        public PartitionInfo parseDir(String dir) {
            Matcher m = p.matcher(dir);
            if(m.matches()) {
                int year = Integer.parseInt(m.group(1));
                return getPartitionInfo(year);
            } else {
                return null;
            }
        }

        private PartitionInfo getPartitionInfo(int year) {
            PartitionInfo pinfo=new PartitionInfo();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(Calendar.YEAR, year);
            pinfo.partitionStart = TimeEncoding.fromCalendar(cal);
            cal.add(Calendar.YEAR, 1);
            pinfo.partitionEnd = TimeEncoding.fromCalendar(cal);
            pinfo.dir = String.format("%4d", year);
            return pinfo;
        }
    }
}