HTTP Server
===========

Embedded HTTP server that supports static file serving, authentication and API requests.

The HTTP Server is tightly integrated with the security system of Yamcs and serves as the default interface for external tooling wanting to integrate. This covers both server-to-server and server-to-user communication patterns.

The HTTP Server can be disabled when its functionality is not needed. Note that in this case also official external clients such as Yamcs Studio will not be able to connect to Yamcs.


Class Name
----------

:javadoc:`org.yamcs.http.HttpServer`


Configuration
-------------

This is a global service defined in ``etc/yamcs.yaml``. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.http.HttpServer
        args:
          port: 8090
          webSocket:
            writeBufferWaterMark:
              low: 32768
              high: 65536
            connectionCloseNumDroppedMsg: 5
          cors:
            allowOrigin: "*"
            allowCredentials: false


Configuration Options
---------------------

port (integer)
    The port at which Yamcs may be reached. Default: ``8090``

contextPath (string)
    Path string prepended to all routes. For example, a contextPath of ``/yamcs`` will make the api available on ``/yamcs/api`` instead of the default ``/api``. When using this property in combination with a reverse proxy, you should ensure that the proxy path matches with the context path because rewriting may lead to unexpected results.
    
    Use this property only as a last resort. For multi-hosting situations, it is preferable to serve Yamcs from the root of its own subdomain.

zeroCopyEnabled (boolean)
    Indicates whether zero-copy can be used to optimize non-SSL static file serving. Default: ``true``

webSocket (map)
    Configure WebSocket properties. Detailed below. If unset, Yamcs uses sensible defaults.

cors (map)
    Configure cross-origin resource sharing for the HTTP API. Detailed below. If unset, CORS is not supported.


WebSocket sub-configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^

maxFrameLength (integer)
    Maximum frame length in bytes. Default: ``65535``

writeBufferWaterMark (map)
    Water marks for the write buffer of each WebSocket connection. When the buffer is full, messages are dropped. High values lead to increased memory use, but connections will be more resilient against unstable networks (i.e. high jitter). Increasing the values also help if a large number of messages are generated in bursts. The map requires keys ``low`` and ``high`` indicating the low/high water mark in bytes.

    Default: ``{ low: 32768, high: 65536}``

connectionCloseNumDroppedMsg (integer)
    Allowed number of message drops before closing the connection. Default: ``5``


CORS sub-configuration
^^^^^^^^^^^^^^^^^^^^^^

CORS (cross-origin resource sharing) facilitates use of the API in client-side applications that run in the browser. CORS is a W3C specification enforced by all major browsers. Details are described at `<https://www.w3.org/TR/cors/>`_. Yamcs simply adds configurable support for some of the CORS preflight response headers.

Note that the embedded web interface of Yamcs does not need CORS enabled, because it shares the same origin as the HTTP API.

allowOrigin (string)
    Exact string that will be set in the ``Access-Control-Allow-Origin`` header of the preflight response.

allowCredentials (boolean)
    Whether the ``Access-Control-Allow-Credentials`` header of the preflight response is set to true. Default: ``false``
