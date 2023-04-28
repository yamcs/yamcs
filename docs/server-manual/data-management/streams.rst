Streams
=======

The concept of *streams* was inspired from the domain of Complex Event Processing (CEP) or Stream Processing. Streams are similar to database tables, but represent continuously moving data. SQL-like statements can be defined on streams for filtering, aggregation, merging or other operations. Yamcs uses streams for distributing data between all components running inside the same :abbr:`JVM (Java Virtual Machine)`. The most important place where streams are used is to make the connection between the data links and processors.

Typically there is a stream for realtime telemetry called ``tm_realtime``, one for realtime processed parameters called ``pp_realtime``, one for commands called ``tc``, etc.

At instance startup, Yamcs will automatically create all the standard streams specified in the streamConfig property. 

.. code-block:: yaml

    streamConfig:
        tm:
            - name: "tm_realtime"
              processor: "realtime"
            - name: "tm2_realtime"
              rootContainer: "/YSS/SIMULATOR/tm2_container"
              processor: "realtime"
            - name: "tm_dump"
        tc: 
            - name: tc_sim
              processor: realtime
              tcPatterns: ["/YSS/SIMULATOR/.*"]
            - name: tc_tse
              processor: realtime
        invalidTm: "invalid_tm_stream"
        cmdHist: ["cmdhist_realtime", "cmdhist_dump"]
        event: ["events_realtime", "events_dump"]
        param: ["pp_realtime", "pp_tse", "sys_param", "proc_param"]
        parameterAlarm: ["alarms_realtime"]
        eventAlarm: ["event_alarms_realtime"]
        sqlFile: "etc/extra_streams.sql"
        
            
The configuration contains an entry for each default stream type:

tm (list)
    contains a list of TM streams. Each stream has an mandatory name, and an optional processor and rootContainer properties. The processor property is used to attach the stream to a specific processor. If no processor is specified, the stream can still be used for example for recording the data in the archive  - this is typical for a dump stream that retrieves non realtime data. The rootContainer property specifies which XTCE container shall be used for processing the packets on this stream. 
    

tc (list)
    contains a list of TC streams. Each stream has a mandatory name and an optional processor and tcPatterns properties. The processor is used to attach the stream to a specific processor. If no processor is specified, the stream can be used by other services. For example the CFDP service will push the CFDP PDUs to a stream from which they can be copied to a TC stream using some sql commands (as demonstrated in the cfdp example).
    The tcPatterns property is used to determine which command will be sent via this stream. It contains a list of regular expressions which are matched against eh command fully qualified name. If the patterns are not specified, it means that all commands will match.
    The ordering of the streams in this list is important because once a command has matched one stream, the other streams are not checked.

invalidTm (list)
    list of streams on which invalid telemetry packets are sent. These may be used in the data links configuration, to allow saving the telemetry packets which are declared by the preprocessor as invalid (and thus not sent for further processing on the normal tm stream).

cmdHist (list)
    streams used for the command history. No additional option in addition to the stream name is supported.


event
    streams used for events. No additional option in addition to the stream name is supported.
    
param
    streams used for parameters. No additional option in addition to the stream name is supported.

parameterAlarm
    streams used for parameter alarms. No additional option in addition to the stream name is supported.

eventAlarm
    streams used for event alarms. No additional option in addition to the stream name is supported.

sqlFile (string)
    this is not a stream type but a reference to a file containing Stream sql statements that will be executed on instance startup. The file can create additional (non-standard) streams or tables.
