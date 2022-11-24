Container Definitions
=====================

Containers are the equivalent of packets in the usual terminology.

A container employs two mechanism to overcome limitations of the traditional "packet with parameters" approach. These mechanisms are *aggregation* and *inheritance*.


Container Aggregation
---------------------

A container contains sequence entries which can be of two types:

#. **Parameter entries** pointing to normal parameters.
#. **Container entries** pointing to other containers which are then included in the big container.

Special attention must be given to the specification of positions of entries in the container. For performance reasons, it is preferable that all positions are absolute (i.e. relative to the beginning of the container) rather than relative to the previous entry. The Excel spreadsheet loader tries to transform the relative positions specified in the spreadsheet into absolute positions.

However, due to entries which can be of variable size, the situation cannot always be avoided. When an entry whose position is relative to the previous entry is subscribed, Yamcs adds to the subscription all the previous entries until it finds one whose position is absolute.

If an entry's position depends on another entry (it can be the same in case the entry repeats itself) which is a Container Entry (i.e. makes reference to a container), and the referenced container doesn't have the size in bits specified, then all the entries of the referenced container plus all the inheriting containers and their entries recursively are added to the subscription. Thus, the processing of this entry will imply the extraction of all parameters from the referenced container and from the inheriting containers. The maximum position reached when extracting entries from the referenced and inheriting containers is considered the end of this entry and used as the beginning of the following one.


Container Inheritance
---------------------

Containers can point to another container through the baseContainer property, meaning that the baseContainer is extended with additional sequence entries. The inheritance is based on a condition put on the parameters from the baseContainer (e.g. a EDR_HK packet is a CCSDS packet with ``apid=943`` and ``packetid=0x1300abcd``).


Little Endian Parameter Encoding
--------------------------------
Yamcs supports only little or big endian (XTCE allows in addition arbitrary byte orders, this is not supported).

For little endian parameters which occupy a non-integer number of bytes, the following algorithm is applied to extract the parameter from the packet:

#. Based on the location of the first bit and on the size in bits of the parameter, find the sequence of bytes that contains the parameter. Only parameters that occupy at most 4 bytes are supported.

#. Read the bytes in reverse order in a 4 bytes int variable.

#. Apply the mask and the shift required to bring the parameter to the rightmost bit.

For example, assume this C struct on an x86 CPU:

.. code-block:: c

    struct {
        unsigned int parameter1:4;
        unsigned int parameter2:16;
        unsigned int parameter3:12;
    } x;
    x.a=0x1;
    x.b=0x2345;
    x.c=0x678;


When converted to network order, this would give the sequence of hex bytes `51 34 82 67`. Thus, the definition of this packet should look like:

.. list-table::
    :header-rows: 1
    :widths: 50 25 25

    * - Parameter
      - Location
      - Size
    * - parameter1
      - 4
      - 4
    * - parameter2
      - 4
      - 16
    * - parameter3
      - 16
      - 12
