Remote User AuthModule
======================

This AuthModule supports the login of users based on a provided HTTP header containing the username. Currently, it can only be used for API requests, and not for accessing the Yamcs UI.

The header is only honored on requests coming from a trusted proxy, as configured with the ``trustedProxies`` option of the :doc:`../../services/global/http-server`. By default only a reverse proxy running on the same host as Yamcs is trusted. If your reverse proxy runs on a different host or in a different container, add its address to ``trustedProxies``.

.. warning::
    The reverse proxy must remove any occurrence of this header from incoming client requests, and only set it after it has authenticated the user.


Class Name
----------

:javadoc:`org.yamcs.security.RemoteUserAuthModule`


Configuration Options
---------------------

header (string)
    | Name of the HTTP request header that indicates the remotely identified user.
    | Default: ``X-REMOTE-USER``
