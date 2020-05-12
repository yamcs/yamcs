Configuration
=============

Yamcs configuration files are written in YAML format. This format allows to encode in a human friendly way the most common data types: numbers, strings, lists and maps. For detailed syntax rules, please see https://yaml.org.

The root configuration file is ``etc/yamcs.yaml``. It contains a list of Yamcs instances. For each instance, a file called ``etc/yamcs.instance-name.yaml`` defines all the components that are part of the instance. Depending on which components are selected, different configuration files are needed.


Server Configuration
--------------------

The number of configuration options in ``etc/yamcs.yaml`` are realatively limited. A sample configuration file is below.

.. code-block:: yaml

    services:
        - class: org.yamcs.http.HttpServer
          args:
          port: 8090

    instances:
        - simulator

    dataDir: /storage/yamcs-data
    
    incomingDir: /storage/yamcs-incoming


    secretKey: "changeme"

    yamcs-web:
        tag: DEMO
        displayPath: displays
        stackPath: stacks
        features:
            cfdp: true

        staticRoot: ../../../yamcs-web/src/main/webapp/dist

The following options are supported

services (list)
    A list of global services. Users can create their own global services that are unique for the whole Yamcs instance. The global services description can be found in :doc:`../services/global/index`
  
instances (list)
    A list of instances loaded at Yamcs start. It is also possible to load instances from <dataDir>/instance-def directory. The instances created created via the API will be stored there.
    
dataDir (string)
    A directory which will be the root of the Yamcs archive. The directory must exist and it shall be possible for the user who runs Yamcs to write into it. More information about the Yamcs archive can be found in :doc:`../data-management/index`.
    In addition to the directories used for the archive, there are two directories named ``instance-def`` and ``instance-templates`` which are used for the dynamic creation of instances.

incomingDir (string)
    A directory used by the :javadoc:`~org.yamcs.tctm.FilePollingTmDataLink` to load incoming telemetry files. This is a relic from when Yamcs was used only for that; the option should be specified in the link configuration instead (it is left here because some users are quite accustomed to it).

secretKey (string)
    A key that is used to sign the authentication tokens given to the users. It should be changed immediately after installation. As of version 4.11.0 Yamcs does not support persisted authentication tokens but this feature will be available in a future version. 

yamcs-web (map)
    Configuration of the yamcs web application. The different options are documented in :doc:`../web-interface/index`
    
        
Instance Configuration
----------------------

The instance configuration file ``yamcs.<instance-name>.yaml`` contains most of the options that need to be set on a Yamcs server.

.. code-block:: yaml
    
    services:
        - class: org.yamcs.archive.XtceTmRecorder
        ...

    
    dataLinks:
        - name: tm_realtime
          enabledAtStartup: false
          class: org.yamcs.tctm.TcpTmDataLink
          ....
          
    mdb:
        - type: "sheet"
          spec: "mdb/simulator-ccsds.xls"
          subloaders: 
               - type: "sheet"
                 spec: "mdb/simulator-tmtc.xls"
          ....
          
    streamConfig:
        tm:
          - name: "tm_realtime"
            processor: "realtime"
          - name: "tm2_realtime"
            rootContainer: "/YSS/SIMULATOR/tm2_container"
            processor: "realtime"
          - name: "tm_dump"
        cmdHist: ["cmdhist_realtime", "cmdhist_dump"]
        
        
The following options are supported

services (list)
    A list of instance specific services. Each service is specified by a class name and arguments which are passed to the service at initialization. Services are implementations of :javadoc:`~org.yamcs.YamcsService`. Users can create their own services; most of the missions where Yamcs has been used required the creation of at least a mission specific service. More description of available services can be found in :doc:`../services/instance/index`.
         
dataLinks (list)
    A list of data links - these are components of Yamcs responsible for receiving/sending data to a target system. Sometimes users need to create additional data links for connecting via different protocols (e.g. MQTT). The available data links are documented in :doc:`../links/index`
    
mdb (list)
    The configuration of the Mission Database (MDB). The configuration is hierarchical, each loader having the possibility to load sub-loaders which become child Space Systems. More information about the MDB can be found in :doc:`../mdb/index`

    
streamConfig(map)
    This configures the list of streams created when Yamcs starts. The map contains an entry for each standard stream type (``tm``, ``cmdHist``, ``event``, etc) and additionally a key ``sqlFile`` can be used to load a StreamSQL file where user defined streams can be created. More information should be found in :doc:`../data-management/streams`
    
    
