Time in Yamcs
=============

The text below documents several aspects of working with time in Yamcs.


Time Encoding
-------------

Yamcs uses signed eight-byte integers (long in Java) for representing milliseconds since 1-Jan-1970 00:00:00 TAI, including leap seconds. The Yamcs time in milliseconds is the UNIX time (in milliseconds) + leap seconds. 

To convert accurately between TAI and UTC, a leap second table is used. Yamcs parses this information from the configuration file :file:`etc/UTC-TAI.history` in :abbr:`IERS (International Earth Rotation and Reference Systems Service)` format:

* https://hpiers.obspm.fr/iers/bul/bulc/UTC-TAI.history

Upcoming leap seconds are announced biannually in Bulletin C publications:

* https://www.iers.org/IERS/EN/Publications/Bulletins/bulletins.html

The user is responsible for updating manually this file if it changes (when new leap seconds are added). Fortunately this is not very often and new leap seconds are announced well in advance. For example there has been no new leap second between 2017 and 2023.

.. note::

    If the file is not present, Yamcs uses the leap second information that was valid at the time of the software release.


.. rubric:: When a leap second is announced

#. Download the latest :file:`UTC-TAI.history` file from IERS.
#. Deploy this file to :file:`etc/UTC-TAI.history` under the Yamcs directory.
#. Restart Yamcs
#. Verify the leap second table in :doc:`Admin Area <../web-interface/admin/leap-seconds>`.

Yamcs also has a high resolution time implemented in the class :javadoc:`org.yamcs.time.Instant`. This is represented as :math:`8+4` bytes milliseconds and picoseconds of the millisecond. It is not widely used - in Java it is not even easily possible to get the time with a resolution better than millisecond. 

The higher resolution time is sent sometimes from external systems. For example a Ground Station may timestamp the incoming packets with a microsecond or nanosecond precise time (derived from an atomic clock). This time is available as the Earth Reception Time via the yamcs-sle plugin.

The class that allows working with times, offering conversion functionality between the Yamcs time and UTC is :javadoc:`org.yamcs.utils.TimeEncoding`.


Wall clock time
---------------

The wall clock time is the computer time converted to Yamcs format. The ``getWallclockTime()`` function in ``TimeEncoding`` can be used to get the current wallclock time. In practice, in 2024, the following is true:

.. code-block:: java

   TimeEncoding.getWallclockTime() = System.currentTimeMillis() + 37000.

Note that Linux usually does time *smearing* around the leap seconds. This shortens the duration of the second for several hours prior and several hours post the the leap second, to accommodate the extra second. Yamcs does not take the smearing into account, therefore the ``getWallclockTime()`` does not return entirely accurate times when the smearing takes place.


Mission Time
------------

The mission time in Yamcs is the *current* time. For a realtime mission that would be the wall clock time. For a simulation it would be the simulation time.

The mission time is specific to a Yamcs instance and is given by the  :javadoc:`org.yamcs.time.TimeService` configured in that instance. The time service is configured using the ``timeService`` keyword in :file:`etc/yamcs.{instance}.yaml`.

There are two time services implemented as part of standard Yamcs:

* :javadoc:`org.yamcs.time.RealtimeTimeService` - it uses always the wall clock time (the computer time) as the mission time.
* :javadoc:`org.yamcs.time.SimulationTimeService` - this allows to run a simulated time at arbitrary speeds. The time can be set externally via the :apidoc:`HTTP API <time/set-time>` or from a TM data link. Since Yamcs 5.6.1 it is possible to synchronize the mission time between two instances on two different Yamcs servers via the replication service.

Plugins may come with their own implementation of a time service.


Processor Time
--------------

The processor time is the time visible in the Yamcs web application. For realtime processors it is the same as the mission time. For replay processors is the time of the replay, extracted from the packets or parameters as they are read from the archive.


Reception Time
--------------

The reception time is the time associated to data (packets, parameters, events) as it comes into Yamcs. The reception time is always set to mission time.


Generation Time
---------------

The generation time is the time when the data has been generated.

For telemetry packets, it is set by the pre-processor, normally with a time extracted from the packet. However it can be set to the mission time if the ``useLocalGenerationTime`` option is set to true.

The timeEncoding option is used on the TM links to configure how to extract the time from the packet - which means how to convert a number (or more numbers) extracted from the packet to a Yamcs time. The various options for time decoding are documented in the :doc:`../links/packet-preprocessor`

Spacecrafts that have no means to synchronize time (e.g. no access to GPS) will usually use a free running on-board clock (initialized to 0 at startup) to timestamp the packets. In these cases, the on-board time needs to be correlated with the mission time. The :doc:`../services/instance/time-correlation` can be used for this purpose.

Finally, the TM links have an option ``updateSimulationTime`` which can be used to set the mission time to the time extracted from the packet. This works if the SimulationTimeService is used. 


Earth Reception Time
--------------------

The earth reception time is the time a TM packet has been received in a ground station. The TM links are responsible for setting this on the packet inside Yamcs. For example the :abbr:`SLE (Space Link Extension)` TM link (part of the yamcs-sle plugin) will receive the earth reception time via the SLE protocol. 

The earth reception time is a high resolution time which may be used in the process of time correlation.
