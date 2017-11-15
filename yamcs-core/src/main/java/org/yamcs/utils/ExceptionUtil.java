package org.yamcs.utils;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import com.google.common.util.concurrent.UncheckedExecutionException;

public class ExceptionUtil {
    public static Throwable unwind(Throwable t) {
        while (((t instanceof ExecutionException) || (t instanceof CompletionException) || (t instanceof UncheckedExecutionException))
                && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

}
