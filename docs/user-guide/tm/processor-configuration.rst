Process Configuration
=====================

Processors can be of different types which are configured in `processor.yaml`. Usually there is a type called "realtime" which configures the processor with the same name.

.. code-block:: yaml
    :caption: processor.yaml

    realtime:
      telemetryProvider:
        class: org.yamcs.tctm.YarchTmPacketProvider
        args:
          stream: "tm_realtime"
      commandReleaser:
        class: org.yamcs.tctm.YarchTcCommandReleaser
        args:
          stream: "tc_realtime"
      parameterProviders:
        #- class: org.yamcs.tctm.YarchPpProvider
        #  args:
        #    stream: "pp_realtime"
        # implements XTCE algorithms
        - class: org.yamcs.algorithms.AlgorithmManager
        # implements provider of parameters from sys_var stream (these are collected and sent on this stream by SystemParametersCollector     service)
        - class: org.yamcs.parameter.SystemParametersProvider
      config:
        #check alarms and also enable the alarm server (that keeps track of unacknowledged alarms)
        alarm:
          check: true
          server: enabled
        parameterCache:
          enabled: true
          cacheAll: true


Parameter Cache
---------------

The parameterCache options can be used to enable or disable the cache. For a realtime channel the cache is a good idea. For a retrieval channel, the cache is usually disabled for attaining better performance (because only some parameters have to be extracted from packets).

If the cacheAll option is set to false, the Parameter Cache will only contain the values for the subscribed parameters. If a parameter is not subscribed, trying to retrieve a value from cache will return nothing.
When cacheAll is set to true, Yamcs will decode and cache all parameters.
