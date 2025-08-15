Packet Preprocessor
===================

When a data link has identified an incoming packet, it is next provided to a so called *preprocessor*.

A preprocessor is responsible for error detection, possibly correction, and for establishing general information about the packet without relying on the Mission Database:

Generation time
   Represents the time when the packet was generated on-board the spacecraft.
Sequence count
   A number to allow discerning two packets with the same timestamp.

The generation time and sequence count are used as primary key in the ``tm`` table containing archived packets. That means they have to uniquely identify a packet. If the archive receives a new packet with the same (generation time, sequence count) as an existing packet in the archive, it will be considered a duplicate and discarded.

.. rubric:: Generation Time

A particular difficulty when writing a preprocessor is dealing with the generation time. Yamcs originated in the :abbr:`ISS (International Space Station)` world where all the payloads and instruments are time synchronized to GPS and each packet sent to ground has a reliable timestamp. This is of course not true for all spacecrafts - most on-board computer have just an internal clock count which resets to 0 when the computer is restarted.
 
The Yamcs archive needs the generation time for all its functions, not having it means that a large part of the functionality of Yamcs is not usable.
 
There are different strategies to synchronize on-board time with  the ground:
 
Do not attempt to synchronize the time

   The preprocessor can use local generation (computer) reception time as generation time. The on-board time will be still available as a parameter if defined in the MDB. This method is especially useful when using Yamcs as part of a test and check-out system. The system under test might be incomplete and have no (reliable) clock at all. The disadvantage is that when receiving data in non-realtime (e.g. recorded on board or in a ground station), it will not fit orderly in the archive.

Synchronize the on-board system to the ground each time it resets

   This is the method used by :abbr:`cFS (Core Flight System)`. It allows setting a :abbr:`SCTF (Spacecraft Time Correction Factor)` on-board and will correlate the on-board time with ground time.

Maintain a correlation factor on ground

   This is the method specified by :abbr:`ESA (European Space Agency)` PUS standard. In this case the packet preprocessor has to implement the time correlation. The :doc:`../../services/instance/time-correlation` can be used to correlate the on-board time with ground time.
 
Regardless of which method is used, it is important that the preprocessor does not generate packets with wrong timestamps. These might be difficult to locate and remove from the archive later.

.. rubric:: Sequence Count

The sequence count is used to distinguish two packets that have the same timestamp. It does not need to be incremental. For example the :doc:`pus` uses the first 4 bytes of the CCSDS primary header (containing APID and CCSDS sequence count among others) as sequence count for the telemetry stream.


.. toctree::
    :maxdepth: 1
    :caption: Shared Functionality

    time-encoding

.. toctree::
    :maxdepth: 1
    :caption: Packet Preprocessor Implementations

    cfs
    csp
    pus
    generic
