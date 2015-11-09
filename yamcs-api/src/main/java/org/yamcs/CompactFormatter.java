package org.yamcs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;


public class CompactFormatter extends Formatter {
	SimpleDateFormat sdf=new SimpleDateFormat("MMM dd HH:mm:ss.SSS");
	Date d=new Date();
	@Override
	public String format(LogRecord r) {
		d.setTime(r.getMillis());
		StringBuffer sb=new StringBuffer();
		String name;
		name=r.getLoggerName();
		sb.append(sdf.format(d)).append(" [").append(r.getThreadID()).append("] ").append(name).append(" ").append(r.getSourceMethodName()).append(" ").append(r.getLevel()).append(":").append(r.getMessage());
		Object[] params=r.getParameters();
		if(params!=null) {
			for(Object p:params) {
				sb.append(p.toString());
			}
		}
		Throwable t=r.getThrown();
		if(t!=null) {
		    sb.append(": ").append(t.toString()).append("\n");
		    for(StackTraceElement ste:t.getStackTrace()) {
		        sb.append("\t").append(ste.toString()).append("\n");
		    }
		    Throwable cause=t.getCause();
		    while(cause!=null && cause!=t) {
		        sb.append("Caused by: ").append(cause.toString()).append("\n");
		        for(StackTraceElement ste:cause.getStackTrace()) {
		            sb.append("\t").append(ste.toString()).append("\n");
		        }
		        cause=cause.getCause();
		    }
		}
		sb.append("\n");
		return sb.toString();
	}

}
