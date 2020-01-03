Export Packets
=======================

Export a raw packet dump::

    GET /api/archive/{instance}:exportPackets


.. rubric:: Parameters

name (array of strings)
    The archived name of the packets. Names must match exactly. Both these notations are accepted:

    * ``?name=/YSS/SIMULATOR/DHS,/YSS/SIMULATOR/Power``
    * ``?name[]=/YSS/SIMULATOR/DHS&name[]=/YSS/SIMULATOR/Power``

start (string)
    Filter the lower bound of the packet's generation time. Specify a date string in ISO 8601 format. This bound is inclusive.

stop (string)
    Filter the upper bound of the packet's generation time. Specify a date string in ISO 8601 format. This bound is exclusive.

