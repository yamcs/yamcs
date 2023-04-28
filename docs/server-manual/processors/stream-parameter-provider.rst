Stream Parameter Provider
=========================

Provides parameters received from the configured ``param`` stream.


Class Name
----------

:javadoc:`org.yamcs.tctm.StreamParameterProvider`


Configuration
-------------

This service is defined in :file:`etc/processor.yaml`. Example:

.. code-block:: yaml

    realtime:
      services:
        - class: org.yamcs.tctm.StreamParameterProvider
          args:
            stream: "pp_realtime"


Configuration Options
---------------------

streams (list of strings)
    **Required.** The streams to read.
