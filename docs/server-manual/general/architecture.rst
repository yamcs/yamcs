Server Architecture
===================

The Yamcs server runs as a single Java process and it incorporates an embedded HTTP server implemented using `Netty <netty.io>`_.

The main components are depicted in the diagram below.

.. image:: _images/yamcs-server.png
    :alt: Yamcs Server Architecture
    :align: center


Instances
---------

The Yamcs instances provide means for one Yamcs server to monitor/control different payloads or satellites or version of the payloads or satellites at the same time.

Most of the components of Yamcs are instance-specific.


Data Links
----------

Data Links are components that connect to the target system (instruments, ground stations, lab equipment, etc). One Yamcs instance will contain multiple data links. There are three types of data received/sent via Data Links:
 * Telemetry packets.
 * telecommands.
 * Parameters, also called processed parameters. 

Connecting via a protocol to a target system means implementing a specific data link for that protocol. That is why in Yamcs there are some built-in Data Links for UDP, TCP and Artemis. 

The pre-processors run inside the data links and are responsible for reading some basic packet characteristics which are not described in the Mission Database.
  
 
Streams
-------

Streams are components inside Yamcs that transport tuples. They are used to de-couple the producers from the consumers, for example the Data Links from the Processors. The de-coupling allows the user to change the data while being passed from one component to another.


Processors
----------

The Yamcs processor is where most of the monitoring and control functions takes place: packets get transformed from into parameters, limits are monitored, alarms are generated, commands are generated and verified, etc. There can be multiple processors in one instance, typically one permanently processing realtime data and other created on demand for replays. 

In particular, the Parameter Archive will create regularly a processor for parameter archive consolidation. 


Mission Database (MDB)
----------------------

The Mission Database contains the description of the telecommands and telemetry including calibration curves, algorithms, limits, alarms, constraints, command pre and post verification.


Services
--------

A service in Yamcs is a Java class that implements the :javadoc:`org.yamcs.YamcsService` interface. The services can be:
 * global meaning they run only once at the level of the server; their definition can be found in ``yamcs.yaml``. One such service is the HttpServer.
 * instance specific meaning that they run once for each Yamcs instance where they are included; their definition can be found in ``yamcs.<instance>.yaml``
 * processor specific meaning they run at the level of the processor; their definition can be found in ``processor.yaml``.
 
User can define their own services by adding a jar with an implemented java class into the Yamcs lib/ext directory.


Plugins
-------

A plugin in Yamcs is a Java class that implements the :javadoc:`org.yamcs.Plugin` interface. The plugin classes are loaded by the Yamcs server at startup before starting any instance. 
Although not required, it is advised that the user creates a plugin with each jar containing mission specific functionality. This will allow to see in the Yamcs web the version of the plugin loaded; the plugin is also the place where the user can register new API endpoints.


Stream Archive
--------------

The Stream Archive is where tuples can be stored. This is a realtime archive, data is inserted as soon as it is received from a stream. It is optimized for storing data sorted by time.


Parameter Archive
-----------------

The Parameter Archive contains values of parameters and is optimized for receiving the value for a limited set of parameters over longer time intervals. The archive is not realtime but is obtained by creating regular replays transforming data from the stream archive via a processor.


Buckets
-------

Buckets are used for storing general data objects. For example the CFDP service will store there all the files received from the on-board system. As for most Yamcs components, there is an REST API allowing the user to work with buckets (get, upload, delete objects).


Extension points
----------------

In the diagram above, there are some components that have a build symbol; these is where we expect mission specific functionality to be added:

 * new data links have to be implemented if the connection to the target system uses a protocol that is not part of Yamcs.
 * packet pre-processor and command post-processor are componenets where the user can implement some specific TM/TC headers, time formats etc. 
 * the Mission Database (MDB) contains the description of telecommands and telemetry and is entirely mission specific. 
 * user defined streams can implement command routing or basic operations on packets (e.g. extracting CLCW from a TM packet).
 * user defined services can add complete new functionality; an example of such functionality is to assemble telemetry packets into files (this is what the CFDP service does, but if the user's system does not use CFDP, a new service can be developed).
 * finally plugins can be used to group together all the mission specific functionality.
