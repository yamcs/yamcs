Time in Yamcs
=============

This section documents several aspects of working with time in Yamcs.


Time Encoding
-------------

Yamcs uses 8 bytes signed integers (long in java) for representing milliseconds since 1-Jan-1970 00:00:00 TAI, including leap seconds. The Yamcs time in milliseconds is the UNIX time (in milliseconds) + leap seconds. 

The leap seconds are loaded from the file UTC-TAI.history in the Yamcs etc directory. This file is part of the so called IERS (International Earth Rotation and Reference Systems Service) Bulletin C and is published here: `<https://hpiers.obspm.fr/iers/bul/bulc/UTC-TAI.history>`_.

The user is responsible for updating manually this file if it changes (when new leap seconds are added). Fortunately this is not very often and new leap seconds are announced well in advance; for instance there has been no new leap second between 2017 and 2022.

Yamcs also has a high resolution time implemented in the class :javadoc:`org.yamcs.time.Instant`. This is represented as 8+4 bytes milliseconds and picoseconds of the millisecond. It is not widely used - in Java it is not even easily possible to get the time with a resolution better than millisecond. 

The higher resolution time is sent sometimes from external systems - for example a Ground Station may timestamp the incoming packets with a microsecond or nanosecond precise time (derived from an atomic clock) - this time is available as the Earth Reception Time via the yamcs-sle plugin.

The class that allows working with times, offering conversion functionality between the Yamcs time and UTC is :javadoc:`org.yamcs.utils.TimeEncoding`.


Wall clock time
--------------

The wall clock time is the computer time converted to Yamcs format. The getWallclockTime() function in the TimeEncoding can be used to get the current wallclock time. In practice, in 2022, the following is true:

.. code-block:: java

  TimeEncoding.getWallclockTime() = System.currentTimeMillis() + 37000.

Note that Linux usually does time "smearing" around the leap seconds - it shortens the duration of the second for sevearal hours prior and several hours post the the leap second, to accomodate the extra second. Yamcs does not take the smearing into account, therefore the getWallclockTime() does not return entirely accurate times when the smearing takes place.

Mission Time
------------

The mission time in Yamcs is the "current" time - for a realtime mission that would be the wall clock time ; for a simulation it would be the simulation time. 

The mission time is specific to a Yamcs instance and is given by the  :javadoc:`org.yamcs.time.TimeService` configured in that instance. The time service is configured using the ``timeService`` keyword in the yamcs.instance.yaml. 

There are two time services implemented as part of standard Yamcs:

 * :javadoc:`org.yamcs.time.RealtimeTimeService` - it uses always the wall clock time (the computer time) as the mission time.
 * :javadoc:`org.yamcs.time.SimulationTimeService` - this allows to run a simulated time at arbitrary speeds. The time can be set externally via the :apidoc:`Time API <time/set-time>` or from a TM data link. Since Yamcs 5.6.1 it is possible to synchronize the mission time between two instances on two different Yamcs servers via the replication service.

Plugins may come with their own implementation of a time service. For example the yamcs-eurosim plugin gets the mission time from Eurosim (a simulation framework).

Processor Time
--------------

The processor time is the time visible in the yamcs-web. For realtime processors is the same as the mission time; for replay processors is the time of the replay - it is extracted from the packets or parameters as they are extracted from the archive.


Reception Time
--------------

The reception time is the time associated to data (packets, parameters, events) as it comes into Yamcs. The reception time is always set to mission time.


Generation Time
---------------

The generation time is the time when the data has been generated.

For telemetry packets, it is set by the pre-processor, normally with a time extracted from the packet. However it can be set to the mission time if the ``useLocalGenerationTime`` option is set to true.

The timeEncoding option is used on the TM links to configure how to extract the time from the packet - which means how to covert a number (or more numbers) extracted from the packet to a Yamcs time. The various options for time decoding are documented in the :doc:`../links/packet-preprocessor`


The spacecrafts which have no mean to syncrhonize time (e.g. no access to GPS) will usually use a free running on-board clock (initialized to 0 at startup) to timestamp the packets. In these cases, the on-board time needs to be correlated with the mission time. The :doc:`../services/instance/time-correlation` can be used for this purpose.

Finally, the TM links have an option ``updateSimulationTime`` which can be used to set the mission time to the time extracted from the packet. This works if the SimulationTimeService is used. 


Earth Reception Time
--------------------

The earth reception time is the time a TM packet has been received in a ground station. The TM links are responsible for setting this on the packet inside Yamcs. For example the SLE TM link (part of the yamcs-sle plugin) will receive the earth reception time via the SLE protocol. 

The earth reception time is a high resolution time which may be used in the process of time correlation.
