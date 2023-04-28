API Routes
==========

This page displays all available API methods.

Method
    Method name in the format ``[SERVICE].[METHOD]``. A service, in this context, is a grouping of functionally-related methods.

Requests
    The total number of completed request, since server start.

Errors
    The number of requests that resulted in server errors. If this counter is not zero, it is of interest to find the appropriate error stacktrace in the Yamcs log.

    Errors originating from the client (bad request, not found) do not count as server errors. An error response to such requests is within expectation,and will not increment this counter.

HTTP
    Mapping towards HTTP of this method in the format ``VERB PATH``.
    
    Internally the Yamcs API implementation is largely agnostic of HTTP. Instead it implements RPC-like services (Remote Procedure Call), which are transcoded from and to HTTP requests.
