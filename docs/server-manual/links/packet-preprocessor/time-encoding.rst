Time Encoding
=============

Some packet preprocessors allow the use of time decoders to customize how to decode time from the packet. This is specified in a preprocessor option named ``timeEncoding``.

In the following example, packets contain 4 leading bytes with unix time in seconds, a multiplier is used to convert the incoming seconds to milliseconds:

.. code-block:: yaml
   :emphasize-lines: 13-17

   dataLinks:
     - name: file-in
       class: org.yamcs.tctm.FilePollingTmDataLink
       stream: tm_dump
       incomingDir: incoming/
       packetInputStreamClassName: org.yamcs.tctm.FixedPacketInputStream
       packetInputStreamArgs:
         packetSize: 50
       packetPreprocessorClassName: org.yamcs.tctm.GenericPacketPreprocessor
       packetPreprocessorArgs:
         byteOrder: LITTLE_ENDIAN
         timestampOffset: 0
         timeEncoding:
           type: FIXED
           size: 4
           multiplier: 1000
           epoch: UNIX
         seqCountOffset: -1
         rootContainer: /myproject/TM


Time decoders are responsible for providing a relative time in milliseconds. The relative time is converted to an absolute time using a specified epoch.

If there is no epoch specified, the time is considered `raw` and the :doc:`../../services/instance/time-correlation` is used for converting the time to an absolute time. This is the case when the on-board time is not synchronized to anything and the time in the packet is the value of an on-board computer clock which is just a counter most likely initialized at 0 when the on-board computer resets. The raw times do not have units, it is up to the time decoder to decide what value to return; the requirement however is to be linearly correlated to the time. The time correlation service will compute the gradient and the offset that can be used to convert the raw value to an absolute time.

There are a few common options for all time decoders:

epoch (string)
    Specifies to which epoch the time relates to. Can be one of:

    * TAI - the time is a delta from 1-Jan-1958, as recommended by CCSDS Time Code Formats.
    * J2000 - the time is a delta from J2000 epoch which corresponds to 2000-01-01T11:58:55.816 UTC.
    * GPS - the time is a delta from GPS epoch which corresponds to 1980-01-6T00:00:00 UTC.
    * UNIX - the time corresponds to the time as kept by UNIX - that is a pseudo-number of seconds from 1-Jan-1970. We say `pseudo` because this time does not include leap seconds and therefore it is not a true delta time from the epoch (and the epoch is anyway not well defined). However that number can be used to calculate a UTC time (by applying Gregorian-calendar conventions). Yamcs will convert that time to the internal time format by adding the leap seconds.
    * CUSTOM - the time corresponds to a delta or pseudo delta specified in the option ``epochUTC``. 
    * NONE - the time read from the packet is not a delta from an epoch but rather the value of free running clock . A time correlation service can be used to translate that value to a real time.
        
epochUTC (ISO8601 string)
    If the epoch is defined as ``CUSTOM``, can be used to specify the UTC time from which the decoded time is a delta or pseudo-delta.
    
timeIncludesLeapSeconds: (boolean)
    If the epoch is defined as ``CUSTOM``, can be used to specify if the time read from that epoch includes the leap seconds (meaning it is a true delta time). If the value is false, Yamcs will add the missing leap seconds between the time specified in the epochUTC and the time read from the packet.

    From the 4 standard epochs (TAI, J2000, GPS and UNIX), only the UNIX time will have this set to false. Default: ``true``


CUC Time Decoder
----------------

CUC is an abbreviation for CCSDS Unsegmented Time Code. *Unsegmented* means that the entire time field can be seen as a continuous integer counter of the fractional time unit. A segmented time code for example  one which provides days and millisecond of the day and in which a 32 bit field is used to represent the millisecond of the day is not continuous because there are less than :math:`2^{32}` milliseconds in a day.
       
The time is decoded as specified in `CCSDS Time Code Formats CCSDS 301.0-B-4 <https://public.ccsds.org/Pubs/301x0b4e1.pdf>`_, Chapter 3.2. In short the time is encoded as an optional 1 or 2 bytes ``pfield`` (preamble field) followed by a 1-7 bytes basic time followed by a 0-10 bytes fractional time. The ``pfield`` specifies the length in bytes of the basic and fractional times.
       
For example ``pfield = 0x2E`` means that the basic time is encoded on 4 bytes and the fractional time is encoded on 2 bytes, making the length of the time in the packet 6 bytes when the ``pfield`` is implicit or 7 bytes when it is part of the packet.
       
The ``pfield`` contains some information about the epoch used. This information is ignored, the epoch is configured with the ``epoch`` option, as described below.

The standard allows in principle more than 2 ``pfield`` bytes but this is not supported (a custom time decoder has to be used in this case).
       
The CUC decoder can work in two modes depending whether the time decoded is a delta time from a configured epoch or the value of a free running on-board clock.
       
If the time decoded is a delta time from a configured epoch (epoch is different than ``NONE``), the CUC decoder assumes the basic time unit to be the second and it decodes the time to a delta or pseudo-delta from the epoch. The precision is milliseconds (as all time storage in Yamcs), irrespective of the precision used in the encoded time - this means that at maximum two bytes of fractional time will be used. If the fractional time is 2 bytes (i.e. each fractional unit is :math:`1/2^{16}` seconds) or more, it will be be down-rounded when converted to Yamcs time. The maximum length of supported basic time is 6 bytes; this is because 7 or more bytes cannot be converted to 64 bits milliseconds.
       
When the decoded time is the value of a free running on-board clock (epoch is ``NONE``), the CUC decoder provides the "raw" time in the unit of the fractional time (without any precision loss). The time is decoded as a big endian value on bn+fn bytes where bt is the number of basic time bytes and fn is the number of fractional time bytes (as read from the ``pfield``). Practically in this case the decoder doesn't make distinction between basic time and fractional time (this works because the time is unsegmented). The value thus obtained is expected to be passed to a :doc:`../../services/instance/time-correlation` which will convert it to an actual time, automatically detecting the unit of the fractional time.
       
The maximum supported length of the "raw" time is 8 bytes,  if the time is encoded on 9 or more bytes, an exception will be thrown in the ``decodeRaw()`` method.

CUC decoder configuration options:

type (string)
    Set to ``CUC``.
    
implicitPField (integer)
    If the ``pfield`` is not encoded in the packet, it can be set by this option.
    
    A value of ``-1`` means that the ``pfield`` is explicitly provided in the packet. 
    
    Default: ``-1``.
    
implicitPFieldCont (integer)
    This can be used to configure the next octet of the ``pfield`` in case the first bit of the first octet (specified above) is 1.
    

FIXED Time Decoder
------------------

The FIXED decoder decodes the time as a signed integer on 4 or 8 bytes and has an optional multiplier to convert the integer to milliseconds. The multiplier is not used when decoding the time as raw time (i.e. when the epoch is NONE).

FIXED decoder options:

type (string)
    Set to ``FIXED``.
    
size (integer)
    number of bytes containing the time. It has to be 4 or 8. Default: ``8``

multiplier (double)
    used to transform the extracted integer to milliseconds. Default: ``1.0``

This decoder will follow the byte order specified at preprocessor level.


FLOAT64 Time Decoder
--------------------

The ``FLOAT64`` decoder decodes the time as a float value in fractional seconds on 8 bytes.

FLOAT64 decoder options:

type (string)
    Set to ``FLOAT64``.

This decoder will follow the byte order specified at preprocessor level.
