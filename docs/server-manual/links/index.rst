Data Links
==========

Data Links represent special components that communicate with the target instrument or spacecraft. There are three types of Data Links: TM, TC and PP (processed parameters). TM and PP receive telemetry packets or parameters and inject them into the realtime or dump TM or PP streams. The TC data links subscribe to the realtime TC stream and send data to the external systems.

Data Links can report on their status and can also be controlled by an operator to connect or disconnect from their data source.

Note that any Yamcs Service can connect to external sources and inject data in the streams. Data links however, can report on their status using a predefined interface and can also be controlled to connect or disconnect from their data source.

Data links are defined in :file:`etc/yamcs.{instance}.yaml`. Example:

.. code-block:: yaml

    dataLinks:
      - name: tm_realtime
        class: org.yamcs.tctm.TcpTmDataLink
        enabledAtStartup: true
        stream: tm_realtime
        invalidPackets: DIVERT
        invalidPacketsStream: invalid_tm_stream
        ....

General configuration options.

name (string)
    **Required.** The name that will be assigned to the link. Each link needs a unique name; the name can be seen in the user interface and can be used for API calls.

class (string)
    **Required.** The name of the class that is implementing the link. The class has to implement the :javadoc:`~org.yamcs.tctm.Link` interface.

enabledAtStartup (boolean)
    If set to false, the link will be disabled at startup. When true, the link will be enabled at startup.

    If unset, the link's enabled/disabled state is restored from a previous run, defaulting to enabled.

stream (string)
    The name of the stream where the data is taken from or injected into.

tmStream (string)
    This is an alternative to *stream*; can be used for links serving more than one of TM, TC or PP (processed parameters).
    
tcStream (string)
     This is an alternative to *stream*; can be used for links serving more than one of TM, TC or PP.
    
ppStream (string)
     This is an alternative to *stream*; can be used for links serving more than one of TM, TC or PP.

invalidPackets (string)
    One of ``DROP``, ``PROCESS`` or ``DIVERT``. Used for TM links to specify what happens with the packets that the pre-processor decides are invalid:

    ``DROP`` means they are discarded.
    
    ``PROCESS`` means they are put on the normal stream (configured with the ``stream`` parameter), same like the valid packets.
    
    ``DIVERT`` means they are put on another stream specified by the option ``invalidPacketsStream``.

invalidPacketsStream (string)
    If ``invalidPackets`` is set to ``DIVERT``, this configures the stream where the packets are sent.

updateSimulationTime (boolean)
    If set to true, the link will update the simulation time using the generation time of each packet received. The SimulationTimeService has to be configured for the instance, otherwise an error will be raised at startup.

Other options are link-specific and documented in their respective sections.
          
.. toctree::
    :maxdepth: 1
    :caption: Table of Contents
    
    packet-preprocessor
    command-post-processor
    file-polling-tm-data-link
    tcp-tc-data-link
    tcp-tm-data-link
    tse-data-link
    udp-parameter-data-link
    udp-tc-data-link
    udp-tm-data-link
    ccsds-frame-processing
    yamcs-cascading
