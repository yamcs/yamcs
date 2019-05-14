Fundamentals
============

Mission Database
----------------
The Mission Database is a dictionary containing the description of all the telemetry packets, parameters and commands. Yamcs implements concepts from the XTCE standard (XML Telemetric & Command Exchange). Although this standard specifies how to describe a mission database in XML, it also prescribes a series of building blocks for such a database: SpaceSystems, containers, parameters, commands, algorithms, etc.

In Yamcs we do not have as a primary goal the compliance with the XML representation (although Yamcs does have an XTCE XML loader) but more to the structure and the concepts of an XTCE database.  
In fact the primary representation for the mission database in Yamcs is an excel spreadsheet because we noticed that many operators prefer to work with such tool. It is however possible to create database loaders for different formats and it has been done many times in order to be interoperable with other tools (integration is key aspect of Yamcs).

.. note::
    XTCE is a standard created by many space agencies and thus it contains quite a wide range of features. The standard is also evolving with version 1.2 having many changes from version 1.1. Yamcs does not implement completely the standard, thus we cannot state compliance with a particular version.

Instances
---------
The Yamcs instances provide means for one Yamcs server to monitor/control different payloads or sattelites or version of the payloads or satellites at the same time. Each instance has a name and a directory where all data from that instance is stored, as well as a specific Mission Database used to process data for that instance. Therefore, each time the Mission Database changes (e.g. due to an on-board software upgrade), a new instance has to be created. One strategy to deal with long duration missions which require multiple instances, is to put the old instances in readonly mode by disabling the components that inject data.


Streams
-------
The concept of *streams* was inspired from the domain of Complex Event Processing (CEP) or Stream Processing. Streams are similar to database tables, but represent continuously moving data. SQL-like statements can be defined on streams for filtering, aggregation, merging or other operations. Yamcs uses streams for distributing data between all components running inside the same JVM.

Typically there is a stream for realtime telemetry called ``tm_realtime``, one for realtime processed parameters called ``pp_realtime``, one for commands called ``tc``, etc.

Streams can be made 'visible' to the external word by two means:

* Apache ActiveMQ Artemis wrappers. There are several services that can take data from streams and publish them to Artemis addresses. Unlike the Yamcs streams which are synchronous and lightweight, Artemis addresses are asynchronous and involve more overhead. Care has to be taken with Artemis for not filling up the memory if clients are slow to read messages from the queue.

* WebSocket subscription. This can be done using the HTTP API documented separately at `https://www.yamcs.org/docs/http/ <https://www.yamcs.org/docs/http/>`_.


Processors
----------
Yamcs processes TM/TC according to Mission Database definitions. Yamcs supports concurrent processing of parallel streams; one processing context is called *Processor*. Processors have clients that receive TM and send TC. Typically one Yamcs instance contains one realtime processor processing data coming in realtime and on-request replay processors, processing data from the archive. Internally, Yamcs creates a replay processors for tasks like filling up the `parameter archive </docs/server/Parameter_Archive>`_.

**Processor Clients** are TM monitoring and/or TC commanding applications (Yamcs Studio, USS, MCS Tools).


Data Links
----------
Data Links represent special components that communicate with the external world. There are three types of Data Links: TM, TC and PP (processed parameters). TM and PP receive telemetry packets or parameters and inject them into the realtime or dump TM or PP streams. The TC data links subscribe to the realtime TC stream and send data to the external systems.
Data Links can report on their status and can also be controlled by an operator to connect or disconnect from their data source.


Data Types
----------

Yamcs supports the following high-level data types:

* A **parameter** is a data value corresponding to the observed value of a certain device. Parameters have different properties like Raw Value, Engineering Value, Monitoring status and Validity status. The raw and engineering values may be of scalar types (i.e int, float, string, etc), or may also be arrays and aggregated parameters (analogous to structs in C programming language).
* A **processed parameter** (abbreviated PP) is a particular type of parameter that is processed by an external (to Yamcs) entity. Yamcs does not contain information about how they are processed. The processed parameters have to be converted into Yamcs internal format (and therefore compatible with the Yamcs parameter types) in order to be propagated to the monitoring clients.
* A **telemetry packet** is a binary chunk of data containing a number of parameters in raw format. The packets are split into parameters according to the definitions contained in the Mission Database.
* **(Tele)commands** are used to control remote devices and are composed of a name and a list of arguments. The commands are transformed into binary packets according to the definition in the Mission Database.
* An **event** is a data type containing a source, type, level and message used by the payload to log certain kind of events. Yamcs generates internally a number of events. In order to extract events from telemetry, a special component called *Event Decoder* has to be written.

The high-level data types described above are modelled internally on a data structure called *tuple*. A tuple is a list of (name, value) pairs, where the names are simple strings and the values being of a few predefined basic data types. The exact definition of the Yamcs high-level data types in terms of tuple (e.g. a telemetry packet has the attributes gentime(timestamp), rectime(timestamp), packet(binary), etc) is currently hard-coded inside the java source code. In the future it might be externalised in configuration files to allow a certain degree of customisation.


Services
--------
Yamcs functionality is modularised into different services, representing objects with operational state, with methods to start and stop. Yamcs acts as a container for services, each running in a different thread. Services carry out a specific function. Some services are vital to core functionality, others can be thought of as more optional and give Yamcs its pluggable nature.

Services appear at three different conceptual levels:

1. **Global services** provide functionality across all instances.
2. **Instance services** provide functionality for one specific instance.
3. **Processor services** provide functionality for one specific processor.
