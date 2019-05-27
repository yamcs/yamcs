Parameter Archive Service
=========================

The Parameter Archive stores time ordered parameter values. The parameter archive is column-oriented and is optimized for accessing a (relatively small) number of parameters over longer periods of time.


Class Name
----------

:javadoc:`org.yamcs.parameterarchive.ParameterArchive`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.parameterarchive.ParameterArchive
        args: 
          realtimeFiller:
            enabled: true
            flushInterval: 300  #seconds
          backFiller:
            #warmupTime: 60 seconds default warmupTime
            enabled: true
            schedule: [{startSegment: 10, numSegments: 3}]

This configuration enables the realtime filler flushing the data to the archive each 5 minutes, and in addition the backFiller fills the archive 10 segments (approx 700 minutes) in the past, 3 segments at a time.

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.parameterarchive.ParameterArchive
        args:
          realtimeFiller:
            enabled: false
          backFiller:
            enabled: true
            warmupTime: 120
            schedule:
              - {startSegment: 10, numSegments: 3}
              - {startSegment: 2, numSegments: 2, interval: 600}

This configuration does not use the realtime filler, but instead performs regular (each 600 seconds) back-fillings of the last two segments. It is the configuration used in the ISS ground segment where due to regular (each 20-30min) LOS (loss of signal), the archive is very fragmented and the only way to obtain continuous data is to perform replays.
