Packet Pre-processor
====================

Yamcs generally uses the Mission Database to process telemetry packets. When data is received from external systems, there are two processing steps done as part of the Data Link which are outside the Mission Database definition:

1. Splitting a data stream into packets. This is done only for the links that receive data as a stream (e.g. TCP). For Data Links where input is naturally split into frames (e.g. UDP) this step is not necessary and not performed.
2. Pre-processing of packets in order to detect/correct errors and to retrieve basic information about the packets.


Stream Splitting
----------------

The data stream splitter is a java class that implements the :javadoc:`~org.yamcs.tctm.PacketInputStream` interface.

A generic splitter for binary streams is defined in :javadoc:`~org.yamcs.tctm.GenericPacketInputStream`. This class can split a stream based on a packet length that is encoded in a header. It requires all packets to have the length on the same number of bytes.


Packet pre-processing
---------------------

The packet pre-processor is a java class that implements the :javadoc:`~org.yamcs.tctm.PacketPreprocessor` interface.
 
It is responsible for error detection (and possibly correction) and extracting basic information required for further packet processing:

* packet generation time: it represents the time when the packet has been generated on-board.
* sequence count: a number used to distinguish two packets having the same timestamp.
 
The generation time and sequence count are used as primary key in the tm table in the archive. That means they have to uniquely identify a packet; if the archive receives a new packet with the same (generation time, sequence count) as an existing packet in the archive, it will be considered a duplicate and discarded.
 
The sequence count is used to distinguish two packets that have the same timestamp; it does not need to be incremental. For example the :javadoc:`~org.yamcs.tctm.IssPacketPreprocessor` uses the first 4 bytes of the CCSDS primary header (containing APID and CCSDS sequence count among others) as sequence count for the telemetry stream.
 
Each mission has specific ways to encode information in the header but there are some standards supported to a certain extent by Yamcs:

* :abbr:`PUS (Packet Utilisation Standard)` from :abbr:`ESA (European Space Agency)`: implemented in :javadoc:`~org.yamcs.tctm.pus.PusPacketPreprocessor`.
* :abbr:`NASA (National Aeronautics and Space Administration)` cFS: implemented in  :javadoc:`~org.yamcs.tctm.cfs.CfsPacketPreprocessor`.
* :abbr:`CSP (CubeSat Space Protocol)`: implemented in  :javadoc:`~org.yamcs.tctm.csp.CspPacketPreprocessor`.


.. rubric:: Generation Time
 
A particular difficulty when writing a pre-processor is dealing with the generation time. Yamcs originated in the :abbr:`ISS (International Space Station)` world where all the payloads and instruments are time synchronized to GPS and each packet sent to ground has a reliable timestamp. This is of course not true for all spacecrafts - most on-board computer have just an internal clock count which resets to 0 when the computer is restarted.
 
The Yamcs archive needs the generation time for all its functions, not having it means that a large part of the functionality of Yamcs is not usable.
 
There are different mechanisms to synchronize the on-board time with the ground:
 
* Do not attempt to synchronize the time. The pre-processor can use local generation (computer) reception time as generation time. The on-board time will be still available as a parameter if defined in the MDB. This method is especially useful when using Yamcs as part of a test and check-out system, the system under test might be incomplete and have no (reliable) clock at all. The disadvantage is that when receiving data in non-realtime (e.g. recorded on board or in a ground station), it will not fit orderly in the archive.
* Synchronize the on-board system to the ground each time it resets. This is the method employed by :abbr:`cFS (Core Flight System)`. It allows setting a spacecraft time correction factor (STCF) on-board and that will make the on-board time correlated to the ground. 
* Maintain a correlation factor on ground, his is the method specified by :abbr:`ESA (European Space Agency)` PUS standard. In this case the packet pre-processor has to implement the time correlation. The :doc:`../services/instance/time-correlation` can be used to correlate the on-board time with the ground time.
 
Regardless of which method is used, it is important that the pre-processor does not generate packets with wrong timestamps. These might be difficult to locate and remove from the archive later.


.. rubric:: Time Decoding

The packet pre-processors can use time decoders to decode the time from the packet. The time decoders are classes implementing the :javadoc:`~org.yamcs.tctm.time.TimeDecoder` interface. All the pre-processors extending the :javadoc:`~org.yamcs.tctm.AbstractPacketPreprocessor` will have access to the time decoders configured by the ``timeEncoding`` option.

The time decoders are responsible for providing a relative time in milliseconds; the relative time is converted to an absolute time using a specified epoch.

If there is no epoch specified, the time is considered ``raw`` and the :doc:`../services/instance/time-correlation` service is used for converting the time to an absolute time. This is the case when the on-board time is not synchronized to anything and the time in the packet is the value of an on-board computer clock which is just a counter most likely initialized at 0 when the on-board computer resets. The raw times do not have units, it is up to the time decoder to decide what value to return; the requirement however is to be linearly correlated to the time. The time correlation service will compute the gradient and the offset that can be used to convert the raw value to an absolute time.

There are a few common options for all time decoders:

epoch (string)
    Specifies to which epoch the time relates to. Can be one of:

    * TAI - the time is a delta from 1-Jan-1958, as recommended by CCSDS Time Code Formats.
    * J2000 - the time is a delta from J2000 epoch which corresponds to 2000-01-01T11:58:55.816 UTC.
    * GPS - the time is a delta from GPS epoch which corresponds to 1980-01-6T00:00:00 UTC.
    * UNIX - the time corresponds to the time as kept by UNIX - that is a pseudo-number of seconds from 1-Jan-1970. We say "pseudo" because this time does not include leap seconds and therefore it is not a true delta time from the epoch (and the epoch is anyway not well defined). However that number can be used to calculate a UTC time (by applying Gregorian-calendar conventions). Yamcs will convert that time to the internal time format by adding the leap seconds.
    * CUSTOM - the time corresponds to a delta or pseudo delta specified in the option ``epochUTC``. 
    * NONE - the time read from the packet is not a delta from an epoch but rather the value of free running clock . A time correlation service can be used to translate that value to a real time.
        
epochUTC (ISO8601 string)
    If the epoch is defined as ``CUSTOM``, can be used to specify the UTC time from which the decoded time is a delta or pseudo-delta.
    
timeIncludesLeapSeconds: (boolean)
    If the epoch is defined as ``CUSTOM``, can be used to specify if the time read from that epoch includes the leap seconds (meaning it is a true delta time). If the value is false, Yamcs will add the missing leap seconds between the time specified in the epochUTC and the time read from the packet.

    From the 4 standard epochs (TAI, J2000, GPS and UNIX), only the UNIX time will have this set to false. Default: true

Two time decoder types are currently implemented: CUC and FIXED.


.. rubric:: CUC time decoder

``CUC`` which is an abbreviation for CCSDS Unsegmented time Code. *Unsegmented* means that the entire time field can be seen as a continuous integer counter of the fractional time unit. A segmented time code for example  one which provides days and millisecond of the day and in which a 32 bit field is used to represent the millisecond of the day is not continuous because there are less than :math:`2^{32}` milliseconds in a day.
       
The time is decoded as specified in `CCSDS Time Code Formats CCSDS 301.0-B-4 <https://public.ccsds.org/Pubs/301x0b4e1.pdf>`_, Chapter 3.2. In short the time is encoded as an optional 1 or 2 bytes ``pfield`` (preamble field) followed by a 1-7 bytes basic time followed by a 0-10 bytes fractional time. The ``pfield`` specifies the length in bytes of the basic and fractional times.
       
For example ``pfield = 0x2E`` means that the basic time is encoded on 4 bytes and the fractional time is encoded on 2 bytes, making the length of the time in the packet 6 bytes when the ``pfield`` is implicit or 7 bytes when it is part of the packet.
       
The ``pfield`` contains some information about the epoch used. This information is ignored, the epoch is configured with the ``epoch`` option, as described below.

The standard allows in principle more than 2 ``pfield`` bytes but this is not supported (a custom time decoder has to be used in this case).
       
The CUC decoder can work in two modes depending whether the time decoded is a delta time from a configured epoch or the value of a free running on-board clock.
       
If the time decoded is a delta time from a configured epoch ( ``epoch`` is different than ``NONE``), the CUC decoder assumes the basic time unit to be the second and it decodes the time to a delta or pseudo-delta from the epoch. The precision is milliseconds (as all time storage in Yamcs), irrespective of the precision used in the encoded time - this means that at maximum two bytes of fractional time will be used. If the fractional time is 2 bytes (i.e. each fractional unit is :math:`1/2^{16}` seconds) or more, it will be be down-rounded when converted to Yamcs time. The maximum length of supported basic time is 6 bytes; this is because 7 or more bytes cannot be converted to 64 bits milliseconds.
       
When the decoded time is the value of a free running on-board clock (epoch is ``NONE``), the CUC decoder provides the "raw" time in the unit of the fractional time (without any precision loss). The time is decoded as a big endian value on bn+fn bytes where bt is the number of basic time bytes and fn is the number of fractional time bytes (as read from the ``pfield``). Practically in this case the decoder doesn't make distinction between basic time and fractional time (this works because the time is unsegmented). The value thus obtained is expected to be passed to a :doc:`../services/instance/time-correlation` which will convert it to an actual time, automatically detecting the unit of the fractional time.
       
The maximum supported length of the "raw" time is 8 bytes,  if the time is encoded on 9 or more bytes, an exception will be thrown in the ``decodeRaw()`` method.

CUC decoder configuration options:

type (string)
    Has to be ``CUC`` to select the CUC decoder.
    
implicitPField (integer)
    If the ``pfield`` is not encoded in the packet, it can be set by this option.
    
    A value of -1 means that the ``pfield`` is explicitly provided in the packet. Default: -1.
    
implicitPFieldCont (integer)
    This can be used to configure the next octet of the ``pfield`` in case the first bit of the first octet (specified above) is 1.
    

.. rubric:: FIXED time decoder

The FIXED decoder decodes the time as a signed integer on 4 or 8 bytes and has an optional multiplier to convert the integer to milliseconds. The multiplier is not used when decoding the time as raw time (i.e. when the epoch is NONE).

FIXED decoder options:

type (string)
    Has to be ``FIXED`` to select the FIXED decoder.
    
size(integer)
    number of bytes containing the time. It has to be 4 or 8. Default: 8

multiplier (double)
    used to transform the extracted integer to milliseconds. Default: 1.0
    

Pre-processor Configuration
---------------------------

The :javadoc:`~org.yamcs.tctm.AbstractPacketPreprocessor` provides some general configuration options which can be used in custom pre-processors and are used in the :abbr:`PUS (Packet Utilisation Standard)` and :abbr:`cFS (Core Flight System)` pre-processors.

.. rubric:: Example

.. code-block:: yaml

  dataLinks:
    - name: tm_realtime
      ...
      packetPreprocessorClassName: org.yamcs.tctm.pus.PusPacketPreprocessor
      packetPreprocessorArgs:
        errorDetection:
           type: CRC-16-CCIIT
        useLocalGenerationTime: false
        timeEncoding:
           type: CUC
           epoch: CUSTOM
           epochUTC: "2010-09-01T00:00:00Z"
           timeIncludesLeapSeconds: true
        tcoService: tco0

 
.. rubric:: Configuration Options
 
errorDetection (map)
    If specified, the *errorDetectionCalculator* object will be made available to the pre-processor to calculate the CRC used to verify the integrity of the packet. The sub-options are:
    
    type (string)
        **Required.** Can take one of the values:

        * ``16-SUM``: calculates a 16 bits checksum over the entire packet which has to contain an even number of bytes. This checksum is used in Columbus/:abbr:`ISS (International Space Station)` data.
        * ``CRC-16-CCIIT``: standard CRC algorithm used in PUS and also in CCSDS standards for frame encoding. 
        * ``ISO-16``: specified in PUS as alternative to CRC-16-CCIIT.
        * ``NONE``: no error detection will be used, this is the default if the ``errorDetection`` map is not present.
    
    initialValue (integer)
       Used when the type is ``CRC-16-CCIIT`` to specify the initial value used for the algorithm. Default: ``0xFFFF``.

userLocalGenerationTime (boolean)
    If true, the packets will be timestamp with local mission time rather than the time extracted from the packets. Default: false.

timeEncoding (map)
    This contains instructions from how to read the time from the packet. See above for description on how to configure the time decoder.
 