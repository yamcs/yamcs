package org.yamcs.web.rest;

import org.yamcs.TimeInterval;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;

/**
 * These methods are looking for a better home
 */
public class RestUtils {
    
    /**
     * Returns true if the request specifies descending by use of the query string paramter 'order=desc'
     */
    public static boolean asksDescending(RestRequest req, boolean descendByDefault) throws HttpException {
        if (req.hasQueryParameter("order")) {
            switch (req.getQueryParameter("order").toLowerCase()) {
            case "asc":
            case "ascending":
                return false;
            case "desc":
            case "descending":
                return true;
            default:
                throw new BadRequestException("Unsupported value for order parameter. Expected 'asc' or 'desc'");
            }            
        } else {
            return descendByDefault;
        }
    }
    
    /**
     * Interprets the provided string as either an instant, or an ISO 8601
     * string and returns it as an instant of type long
     */
    public static long parseTime(String datetime) {
        try {
            return Long.parseLong(datetime);
        } catch (NumberFormatException e) {
            return TimeEncoding.parse(datetime);
        }
    }
    
    public static IntervalResult scanForInterval(RestRequest req) throws HttpException {
        return new IntervalResult(req);
    }
    
    public static class IntervalResult {
        private final long start;
        private final long stop;
        
        IntervalResult(RestRequest req) throws BadRequestException {
            start = req.getQueryParameterAsDate("start", TimeEncoding.INVALID_INSTANT);
            stop = req.getQueryParameterAsDate("stop", TimeEncoding.INVALID_INSTANT);
        }
        
        public boolean hasInterval() {
            return start != TimeEncoding.INVALID_INSTANT || stop != TimeEncoding.INVALID_INSTANT;
        }
        
        public boolean hasStart() {
            return start != TimeEncoding.INVALID_INSTANT;
        }
        
        public boolean hasStop() {
            return stop != TimeEncoding.INVALID_INSTANT;
        }
        
        public long getStart() {
            return start;
        }
        
        public long getStop() {
            return stop;
        }
        
        public TimeInterval asTimeInterval() {
            TimeInterval intv = new TimeInterval();
            if (hasStart()) intv.setStart(start);
            if (hasStop()) intv.setStop(stop);
            return intv;
        }
        
        public String asSqlCondition(String col) {
            StringBuilder buf = new StringBuilder();
            if (start != TimeEncoding.INVALID_INSTANT) {
                buf.append(col).append(" >= ").append(start);
                if (stop != TimeEncoding.INVALID_INSTANT) {
                    buf.append(" and ").append(col).append(" < ").append(stop);
                }
            } else {
                buf.append(col).append(" < ").append(stop);
            }
            return buf.toString();
        }
    }
}
