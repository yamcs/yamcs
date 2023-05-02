Stream TC Command Releaser
==========================

Sends commands to the configured ``tc`` streams. 

The service supports sending commands to multiple streams depending on the command name. Each stream can be connected to a different data link, thus allowing Yamcs to control multiple targets concurrently.

The streams where the commands are sent to are defined as part of the :doc:`streamConfig section<../data-management/streams>` in the :file:`etc/yamcs.{instance}.yaml` instance configuration file.


Class Name
----------

:javadoc:`org.yamcs.StreamTcCommandReleaser`


Configuration
-------------

This service is defined in :file:`etc/processor.yaml`. Example:

.. code-block:: yaml

    realtime:
      services:
        - class: org.yamcs.StreamTcCommandReleaser


Configuration Options
---------------------

stream (string)
    The stream to send commands to. This option is deprecated in favor of the stream configuration defined at instance level. Among others, that configuration is preferred because it allows having different streams for different instances, whereas :file:`etc/processor.yaml` defines this service is common for all instances.
