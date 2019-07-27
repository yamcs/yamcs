Artemis Server
==============

Initializes and starts an embedded instance of the Artemis messaging server. This can be used to connect streams across Yamcs installations.


Class Name
----------

:javadoc:`org.yamcs.artemis.ArtemisServer`


Configuration
-------------

This is a global service defined in ``etc/yamcs.yaml``. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.artemis.ArtemisServer


Configuration Options
---------------------

securityManager (string)
    Class name of a ``org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager`` implementation. The implementation should have a no-arg constructor. If unspecified, security is not enabled.


This service reads further configuration options from a file ``etc/artemis.xml``. This files is passed as-is to the embedded Artemis server.
