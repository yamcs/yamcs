Remote User AuthModule
======================

This AuthModule supports the login of users based on a provided HTTP header containing the username. Currently, it can only be used for API requests, and not for accessing the Yamcs web interface.

.. warning::
    When using this module you must protect Yamcs against spoofing attacks.


Class Name
----------

:javadoc:`org.yamcs.security.RemoteUserAuthModule`


Configuration Options
---------------------

header (string)
    | Name of the HTTP request header that indicates the remotely identified user.
    | Default: ``X-REMOTE-USER``
