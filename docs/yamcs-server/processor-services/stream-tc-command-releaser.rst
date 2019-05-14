Stream TC Command Releaser
==========================

Sends commands to the configured ``tc`` stream.


Class Name
----------

:javadoc:`org.yamcs.StreamTcCommandReleaser`


Configuration
-------------

This service is defined in ``etc/processor.yaml``. Example:

.. code-block:: yaml
    :caption: processor.yaml

    realtime:
      services:
        - class: org.yamcs.StreamTcCommandReleaser
          args:
            stream: "tc_realtime"


Configuration Options
---------------------

stream (string)
    **Required.** The stream to send commands to.
