package org.yamcs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;


/**
 * Specifically intended for short-term console output. It contains the bare minimum of information.
 * Memory optimization is 'good enough' for console output.
 * 
 * Intentional alterations:
 * <ul>
 * <li>Hide the day, only the hour is shown
 * <li>Hide the logger names for central classes
 * <li>Hide the 'INFO' string, since it's redundant. Only show when it's not INFO
 * <li>Hide the method name
 * <li>Support minimal colors
 * </ul>
 */
public class ConsoleFormatter extends Formatter {
    private static final String COLOR_PREFIX= "\033[";
    private static final String COLOR_SUFFIX = "m";
    private static final String COLOR_RESET = "\033[0;0m";
    
	SimpleDateFormat sdf=new SimpleDateFormat("HH:mm:ss.SSS");
	Date d=new Date();
	@Override
	public String format(LogRecord r) {
		d.setTime(r.getMillis());
		StringBuilder sb=new StringBuilder();
		String name=r.getLoggerName();;
		String decoration;
		sb.append(sdf.format(d));
		sb.append(" [").append(r.getThreadID()).append("] ");
		
	    if (name.lastIndexOf('.')!=-1) {
            name=name.substring(name.lastIndexOf('.') + 1);
        }
        if (name.lastIndexOf('[')!=-1) {
            decoration=name.substring(name.lastIndexOf('[') + 1, name.length() - 1);
            name=name.substring(0, name.lastIndexOf('['));
        } else {
            decoration=null;
        }
	    colorize(sb, name, 0, 36);
	    sb.append(" ");
		
		if (decoration!=null) {
		    colorize(sb, decoration, 0, 35);
            sb.append(" ");
		}
		
		if (r.getLevel() == Level.WARNING || r.getLevel() == Level.SEVERE) {
		    colorize(sb, r.getLevel().toString(), 0, 31);
		    sb.append(" ");
		} else if (r.getLevel() != Level.INFO) {
		    sb.append(r.getLevel()).append(" ");
		}
		sb.append(r.getMessage());
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

    private static void colorize(StringBuilder buf, String s, int brightness, int ansiColor) {
        buf.append(COLOR_PREFIX).append(brightness).append(';').append(ansiColor).append(COLOR_SUFFIX);
        buf.append(s);
        buf.append(COLOR_RESET);
    }
}
