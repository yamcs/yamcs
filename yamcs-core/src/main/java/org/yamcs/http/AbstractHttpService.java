package org.yamcs.http;

import org.yamcs.InitException;
import org.yamcs.logging.Log;

import com.google.common.util.concurrent.AbstractService;

/**
 * An HTTP-specific subservice whose lifecycle is managed by {@link HttpServer}.
 * <p>
 * HTTP services may participate in the start-stop phases of the {@link HttpServer}.
 */
public abstract class AbstractHttpService extends AbstractService {

    protected Log log = new Log(getClass());

    public abstract void init(HttpServer httpServer) throws InitException;
}
