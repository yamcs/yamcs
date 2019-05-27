Artemis Server
==============

Initializes and starts an embedded instance of the Artemis messaging server. This can be used to connect streams across Yamcs installations.


Class Name
----------

:javadoc:`org.yamcs.artemis.ArtemisServer`


Configuration
-------------

This is a global service defined in ``etc/yamcs.yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.yaml

    services:
      - class: org.yamcs.artemis.ArtemisServer


Configuration Options
---------------------

configFile (string)
    Filename of the XML configuration file that contains further configuration options. Do not use an absolute path. The file must exist in the ``/opt/yamcs/etc`` folder. Default: ``artemis.xml``.

securityManager (string)
    Class name of a ``org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager`` implementation. The implementation should have a no-arg constructor. If unspecified, security is not enabled.
