Configuration
=============

Yamcs configuration files are written in YAML format. This format allows to encode in a human friendly way the most common data types: numbers, strings, lists and maps. For detailed syntax rules, please see https://yaml.org.

The root configuration file is :file:`etc/yamcs.yaml`. It contains a list of Yamcs instances. For each instance, a file called :file:`etc/yamcs.{instance}.yaml` defines all the components that are part of the instance. Depending on which components are selected, different configuration files are needed.


Server Configuration
--------------------

The number of configuration options in :file:`etc/yamcs.yaml` are relatively limited. A sample configuration file is below.

.. code-block:: yaml

    services:
        - class: org.yamcs.http.HttpServer

    instances:
        - simulator

    dataDir: /storage/yamcs-data

    secretKey: "changeme"

    yamcs-web:
        tag: DEMO

The following options are supported

services (list)
    A list of global services. Users can create their own global services that are unique for the whole Yamcs instance. The global services description can be found in :doc:`../services/global/index`
  
instances (list)
    A list of instances loaded at Yamcs start. It is also possible to load instances from :file:`{dataDir}/instance-def` directory. The instances created created via the API will be stored there.
    
dataDir (string)
    A directory which will be the root of the Yamcs archive. The directory must exist and it shall be possible for the user who runs Yamcs to write into it. More information about the Yamcs archive can be found in :doc:`../data-management/index`.
    In addition to the directories used for the archive, there are two directories named :file:`instance-def` and :file:`instance-templates` which are used for the dynamic creation of instances.

cacheDir (string)
    A directory that Yamcs can use to cache files. Defaults to a directory called :file:`cache` relative to the directory where Yamcs is running from.

secretKey (string)
    A key that is used to sign the authentication tokens given to the users. It should be changed immediately after installation. As of version 5.0.0, Yamcs does not support persisted authentication tokens but this feature will be available in a future version. 

yamcs-web (map)
    Configuration of the Yamcs web application. The different options are documented in :doc:`../web-interface/index`
    
        
Instance Configuration
----------------------

The instance configuration file :file:`etc/yamcs.{instance}.yaml` contains most of the options that need to be set on a Yamcs server.

.. code-block:: yaml
    
    services:
        - class: org.yamcs.archive.XtceTmRecorder
        ...

    dataLinks:
        - name: tm_realtime
          class: org.yamcs.tctm.TcpTmDataLink
          ...

    mdb:
        - type: "sheet"
          spec: "mdb/simulator-ccsds.xls"
          subloaders:
               - type: "sheet"
                 spec: "mdb/simulator-tmtc.xls"
          ...

    streamConfig:
        tm:
          - name: "tm_realtime"
            processor: "realtime"
          - name: "tm2_realtime"
            rootContainer: "/YSS/SIMULATOR/tm2_container"
            processor: "realtime"
          - name: "tm_dump"
        cmdHist: ["cmdhist_realtime", "cmdhist_dump"]

    timeService:
        class: org.yamcs.time.SimulationTimeService
    
    dataPartitioningByTime: YYYY/MM



The following options are supported

services (list)
    A list of instance specific services. Each service is specified by a class name and arguments which are passed to the service at initialization. Services are implementations of :javadoc:`~org.yamcs.YamcsService`. Users can create their own services; most of the missions where Yamcs has been used required the creation of at least a mission specific service. More description of available services can be found in :doc:`../services/instance/index`.
         
dataLinks (list)
    A list of data links - these are components of Yamcs responsible for receiving/sending data to a target system. Sometimes users need to create additional data links for connecting via different protocols (e.g. MQTT). The available data links are documented in :doc:`../links/index`
    
mdb (list)
    The configuration of the Mission Database (MDB). The configuration is hierarchical, each loader having the possibility to load sub-loaders which become child Space Systems. More information about the MDB can be found in :doc:`../mdb/index`
    
streamConfig(map)
    This configures the list of streams created when Yamcs starts. The map contains an entry for each standard stream type (``tm``, ``cmdHist``, ``event``, etc) and additionally a key ``sqlFile`` can be used to load a StreamSQL file where user defined streams can be created. More information can be found in :doc:`../data-management/streams`
    
timeService(map)
    This configures the source of the "mission time". By default the RealtimeTimeService uses the local computer clock as the time source. The :javadoc:`org.yamcs.time.SimulationTimeService` can be used to simulate a mission time in the past or the future. If configured, the time can be controlled using the :apidoc:`HTTP API <time/set-time>`. The ``updateSimulationTime: true`` option on a telemetry data link can also be used to manipulate the simulation time - in this case the time will be set to be the generation time of the packet.
    
dataPartitioningByTime(String)
    One of "none", "YYYY", "YYYY/MM" or "YYYY/DOY"
    If specified, partition the tm, pp, events, alarms, cmdhistory tables and the parameter archive by time. For example, specifying YYYY/MM will store the data of each month into a different RocksdDB database. This option is useful when the archive is expected to grow very large: the new data will not disturb the old data (otherwise RocksDB always merges new files with old ones) and data can be spread over multiple filesystems. 


Configuration Properties
------------------------

A file :file:`etc/application.properties` may be used to define *properties*. These properties can then be referenced in any YAML configuration file. This approach can be useful to separate dynamic aspects from the main configuration file.

For example:

.. code-block:: properties
    :caption: :file:`etc/application.properties`

    # IP address of some simulator
    simulator.host = 192.168.77.7
    simulator.port = 10015

.. code-block:: yaml
    :caption: :file:`etc/yamcs.{instance}.yaml`

    dataLinks:
      - name: tm-in
        class: org.yamcs.tctm.TcpTmDataLink
        stream: tm_realtime
        host: ${simulator.host:localhost}
        port: ${simulator.port}

YAML configuration values may use properties names in the following notations:

``${foo}``
    Expands to a property value. If the file :file:`etc/application.properties` exists, a lookup is attempted for the property ``foo``. If that fails, a lookup is attempted in the standard Java system properties.

    An error is generated if the property cannot be found.

``${foo:bar}``
    Same as ``${foo}``, but defaults to the value ``bar`` when the property could not be found.

``${env.foo}``
    Expands to the value of an environment variable, available to the Yamcs daemon. An error is generated if the environment variable is not set.

``${env.foo:bar}``
    Same as ``${env.foo}``, but defaults to the value ``bar`` when the environment variable is not set.

``${foo:${bar}}``
    Same as ``${foo}``, but defaults to the value of the ``bar`` property.

.. note::
    When properties are defined, the configuration file must remain valid YAML. This may sometimes require surrounding the YAML value with explicit string quotes. The following two notations are identical:

    * ``host: ${simulator.port}``
    * ``host: "${simulator.port}"``
