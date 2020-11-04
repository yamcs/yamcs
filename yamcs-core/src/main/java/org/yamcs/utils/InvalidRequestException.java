package org.yamcs.utils;

/**
 * Exception thrown by services when receiving an invalid request usually via the http api, case in which it transformed
 * into HTTP Bad Request
 * 
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
