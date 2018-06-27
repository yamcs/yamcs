package org.yamcs.xtceproc;

/**
 * exception thrown when an error is encountered during processing xtce containers for extracting parameters out of packets 
 * @author nm
 *
 */
public class ContainerProcessingException extends RuntimeException {
    public ContainerProcessingException(String msg) {
        super(msg);
    }
    
    public ContainerProcessingException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
