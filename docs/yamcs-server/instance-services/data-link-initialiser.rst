Data Link Initialiser
=====================

Manages the various data links and creates needed streams during Yamcs start-up.

Data Links represent data connections to external sources. These connections may represent output flows (TC), input flows (TM, PP) or a combination of these. Data links that read TM and PP data receive telemetry packets or parameters and inject them into the realtime or dump TM or PP streams. Data links that send TC subscribe to a TC stream and send data to external systems.

Note that any Yamcs Service can connect to external sources and inject data in the streams. Data links however, can report on their status using a predefined interface and can also be controlled to connect or disconnect from their data source.


Class Name
----------

:javadoc:`org.yamcs.tctm.DataLinkInitialiser`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.tctm.DataLinkInitialiser

    dataLinks:
      - name: tm_realtime
        class: org.yamcs.tctm.TcpTmDataLink
        args:
          stream: tm_realtime
          host: localhost
          port: 10015
      - name: tm_dump
        class: org.yamcs.tctm.TcpTmDataLink
        args:
          stream: tm_dump
          host: localhost
          port: 10115
      - name: udp_realtime
        class: org.yamcs.tctm.UdpTmDataLink
        args:
          stream: tm_realtime
          port: 5900
          maxLength: 2048
      - name: tc_realtime
        class: org.yamcs.tctm.TcpTcDataLink
        args:
          stream: tc_realtime
          host: localhost
          port: 10025


Each link is defined in terms of an identifying name and a class. There is also a property ``enabledAtStartup`` which allows to enable (default) or disable the TM provider for connecting to the external data source at the server start-up.

Specific data links may support additional arbitrary configuration options via the ``args`` key.
