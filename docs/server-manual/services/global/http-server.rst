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

This is a global service defined in :file:`etc/yamcs.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.http.HttpServer
        args:
          port: 8090
          webSocket:
            writeBufferWaterMark:
              low: 32768
              high: 65536
          cors:
            allowOrigin: "*"
            allowCredentials: false


Configuration Options
---------------------

address (string)
    The local address to which Yamcs will bind waiting for HTTP clients. If unset, Yamcs binds to a wildcard address.

port (integer)
    The port to which Yamcs will bind waiting for HTTP clients. Default: ``8090``

tlsCert (string or list of strings)
    If specified, the server will be listening for TLS connections. TLS is used for encrypting the data.

    In case the file is a bundle containing multiple certificates, the certificates must be ordered from leaf to root.

    Multiple certificate files may also be provided as an array. Again, certificates must then be ordered from leaf to root, between the files and also between certificates within the files.

tlsKey (string)
    **Required** if ``tlsCert`` is specified. The key to the certificate.

contextPath (string)
    Path string prepended to all routes. For example, a contextPath of ``/yamcs`` will make the API available on ``/yamcs/api`` instead of the default ``/api``. When using this property in combination with a reverse proxy, you should ensure that the proxy path matches with the context path because rewriting may lead to unexpected results.

maxContentLength (integer)
    Maximum allowed length of request bodies. This is applied to all non-streaming API requests. Default: ``65536``

    Some routes may specify a custom ``maxBodySize`` option, in which case the maximum of the two values gets applied.

maxInitialLineLength (integer)
    Maximum allowed length of the initial line (for example: ``GET / HTTP/1.1``). Default: ``8192``

maxHeaderSize (integer)
    Maximum allowed length of all headers combined. Default: ``8192``

maxPageSize (integer)
    Maximum allowed page size.

    This corresponds with the ``limit`` query parameter that is used in the HTTP API.

    Default: ``1000``.

nThreads (integer)
    Configure the number of threads that handle HTTP requests. The value ``0`` resolves to two times the number of CPU cores. Default: ``0``

reverseLookup (boolean)
    If enabled, hostnames instead of IP addresses are used to identify clients. Use of this option may trigger name service reverse lookups. Default: ``false``

webSocket (map)
    Configure WebSocket properties. Detailed below. If unset, Yamcs uses sensible defaults.

cors (map)
    Configure cross-origin resource sharing for the HTTP API. Detailed below. If unset, CORS is not supported.


WebSocket sub-configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^

maxFrameLength (integer)
    Maximum frame length in bytes. This is applied to incoming frames. Default: ``65536``

writeBufferWaterMark (map)
    Water marks for the write buffer of each WebSocket connection. When the buffer is full, messages are dropped. High values lead to increased memory use, but connections will be more resilient against unstable networks (i.e. high jitter). Increasing the values also help if a large number of messages are generated in bursts. The map requires keys ``low`` and ``high`` indicating the low/high water mark in bytes.

    Default: ``{ low: 32768, high: 131072 }``

pingWhenIdleFor (integer)
    Idle timeout in seconds (either read or write). When this timeout is met, a WebSocket ping frame is sent to the connected client. This helps prevent unexpected closes by intermediate firewalls or proxies.

    To disable ping frames, set this value to 0.

    Default: ``40``.


CORS sub-configuration
^^^^^^^^^^^^^^^^^^^^^^

CORS (cross-origin resource sharing) facilitates use of the API in client-side applications that run in the browser. CORS is a W3C specification enforced by all major browsers. Details are described at `<https://www.w3.org/TR/cors/>`_. Yamcs simply adds configurable support for some of the CORS preflight response headers.

Note that the embedded web interface of Yamcs does not need CORS enabled, because it shares the same origin as the HTTP API.

allowOrigin (string)
    Exact string that will be set in the ``Access-Control-Allow-Origin`` header of the preflight response.

allowCredentials (boolean)
    Whether the ``Access-Control-Allow-Credentials`` header of the preflight response is set to true. Default: ``false``
