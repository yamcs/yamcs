Processors
==========

Yamcs processes TM/TC according to Mission Database definitions. Yamcs supports concurrent processing of parallel streams; one processing context is called *Processor*. Processors have clients that receive TM and send TC. Typically one Yamcs instance contains one realtime processor processing data coming in realtime and on-request replay processors, processing data from the archive. Internally, Yamcs creates a replay processors for tasks like filling up the Parameter Archive.

Each processor is composed of a set of services with varying functionality.


.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    tm-processing
    command-processing
    alarms
    processor-configuration    
    alarm-reporter
    algorithm-manager
    local-parameter-manager
    replay-service
    stream-parameter-provider
    stream-tc-command-releaser
    stream-tm-packet-provider
