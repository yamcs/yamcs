CFS Event Decoder
=================

Decodes `cFS <https://cfs.gsfc.nasa.gov/>`_ (Core Flight System) events. This service translates binary cFS telemetry packets into Yamcs events.

The packets are filtered by message id (first 2 bytes of the header).

The structure of the event packets is as defined in the `CFE_EVS_LongEventTlm_Payload struct <https://github.com/nasa/cFE/blob/main/fsw/cfe-core/src/inc/cfe_evs_msg.h#L1235>`_. The structure had different names in older versions of cFS.

The field ``EventType`` is used to derive the event severity:

* value 3 is considered severity ``ERROR``
* value 4 is considered severity ``CRITICAL``
* all the other values are considered severity ``INFO``


Class Name
----------

:javadoc:`org.yamcs.tctm.cfs.CfsEventDecoder`


Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.tctm.cfs.CfsEventDecoder
        args:
          msgIds: [0x0808]
          byteOrder: BIG_ENDIAN
          charset: US-ASCII
          appNameMax: 20
          eventMsgMax: 122
          streams:
            - tm_realtime


Configuration Options
---------------------

msgIds ([integer])
    The message ids that will be considered as events. This argument is required.

byteOrder (string):
    The byte order of the event telemetry packet. Default:``BIG_ENDIAN``

charset (string):
    The charset used to decode the text string. Default: ``US-ASCII``

appNameMax (integer):
    The size of the app name in bytes. Default: ``20``

eventMsgMax (integer):
    The size of the event message string in bytes. Default: ``122``

streams ([string]):
    The streams to process for events. Not required. If no stream is provided, all telemetry streams of type ``tm`` are used (these are configured in the instance configuration file under the ``streamConfig`` section).
