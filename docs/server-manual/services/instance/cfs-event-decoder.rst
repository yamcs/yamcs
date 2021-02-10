CFS Event Recorder
==============

Records CFS events. This service stores the data coming from one or more streams into a table ``events``.


Class Name
----------

:javadoc:`org.yamcs.tctm.cfs.CfsEventDecoder`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example:

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

msgIds([integer])
    The message ids that will be considered as events. This argument is required.

byteOrder(string):
    The byte order of the event telemetry packet. Default:``BIG_ENDIAN``

charset(string):
    The charset used to decode the text string. Default: ``US-ASCII``

appNameMax(integer):
    The size of the app name in bytes. Default: ``20``

eventMsgMax(integer):
    The size of the event message string in bytes. Default: ``122``

streams[string]:
    The streams to process for events. Not required. If no stream is provided, all telemetry streams of type ``StandardStreamType`` are used.