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
public interface TimePartitionSchema {
	/**
	 * returns the directory where this instant shall be written.
	 * This is likely to be expensive operation - since instant has to be converted into a calendar taking into accounts leap seconds and all the rest.
	 * 
	 * @param instant
	 * @return
	 */
	public PartitionInfo getPartitionInfo(long instant);
	
	/**
	 * Parses a string of the shape "A/B/..." into a PartitionInfo.
	 * It is used by the storage engines to parse the partitions from disk at startup.
	 * 
	 * Returns null if the given string does not match the expected directory.
	 * @return
	 */
	public PartitionInfo parseDir(String dir);
		
	
	static class PartitionInfo {
		@Override
		public String toString() {
			return "PartitionInfo [dir=" + dir + ", partitionStart="
					+ partitionStart + ", partitionEnd=" + partitionEnd + "]";
		}
		public String dir;
		public long partitionStart;
		public long partitionEnd;
	}
	
	
	
	static class YYYYDOY implements TimePartitionSchema {
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
	
	static class YYYYMM implements TimePartitionSchema {
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
			cal.set(Calendar.MONTH, month);
			pinfo.partitionStart = TimeEncoding.fromCalendar(cal);
			cal.add(Calendar.DAY_OF_YEAR, 1);
			pinfo.partitionEnd = TimeEncoding.fromCalendar(cal);			
			pinfo.dir = String.format("%4d/%02d", year, month);
			return pinfo;
		}
	}
	
	

}



