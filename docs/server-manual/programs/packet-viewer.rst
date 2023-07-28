packet-viewer
=============

.. program:: packet-viewer

Synopsis
--------

.. rst-class:: synopsis

    | *packet-viewer* [<*OPTIONS*>]
    | *packet-viewer* [-l <*N*>] -x <*MDB*> <*FILE*>
    | *packet-viewer* [-l <*N*>] [-x <*MDB*>] -i <*INSTANCE*> [-s <*STREAM*>] <*URL*>


Description
-----------

Use :program:`packet-viewer` to extract parameters from packets by either loading a packet dump from disk (~ offline mode), or by decoding the raw data received from connecting to a Yamcs server (~ online mode).

In *online mode*, the splitting of packets is done by Yamcs Server and :program:`packet-viewer` extracts parameters from each packet binary by using the same logic as Yamcs Server would.

In *offline mode* :program:`packet-viewer` must in addition have access to a local MDB, and requires configuration so that it knows how to decode individual packets from a dump file. By default, dump files are assumed to contain concatenated CCSDS.


Options
-------

.. option:: -h

    Print a help message and exit.

.. option:: -l <N>

    Limit the view to <N> packets.
    
    In *online mode* only the last <N> packets will be visible. The default is 1000.
    
    In *offline mode* only the first <N> packets of the file are displayed. There is no default, but for large dumps :program:`packet-viewer` may become sluggish or run out of heap memory.

.. option:: -x <MDB>

    Name of the applicable MDB as specified in the :file:`etc/mdb.yaml` configuration file.

    This option is required in *offline mode*. In *online* mode the MDB defaults to that of the connected Yamcs instance.

.. option:: -i <INSTANCE>

    In *online mode*, this indicates which instance's telemetry stream :program:`packet-viewer` should connect to.

.. option:: -s <STREAM>

    In *online mode*, this indicates which telemetry stream :program:`packet-viewer` should connect to. 
    
    Default: ``tm_realtime``.

.. option:: <FILE>

    A local file which contains one or more packets. Typically concatenated CCSDS, but other file formats can be defined through configuration.

.. option:: <URL>

    Base URL of a Yamcs server.


Examples
--------

Offline mode:

.. code-block:: console

    packet-viewer -l 50 -x my-db packet-file


Online mode:

.. code-block:: console

    packet-viewer -l 50 -i simulator http://localhost:8090


Configuration Files
-------------------

:program:`packet-viewer` configuration files are placed in the :file:`etc/` directory. MDB files for local packet decoding are placed in :file:`mdb/` directory.

.. code-block:: text

    <packet-viewer>
    |-- bin/
    |-- etc/
    |   |-- mdb.yaml
    |   +-- packet-viewer.yaml
    |-- lib/
    +-- mdb/
        |-- xtce1.xml
        +-- xtce2.xml

mdb.yaml
~~~~~~~~

Specifies one or more MDB configurations, which you can then choose from in order to extract parameters from a packet.

The MDB configuration structure can be copied from a :file:`etc/yamcs.{instance}.yaml` configuration file, but with a level on top which specifies the name visible in UI. In the following example, the user can choose between `mymdb1` and `mymdb2`.

.. code-block:: yaml

    mymdb1:
       - type: "xtce"
         args:
           file: "mdb/xtce1.xml"

    mymdb2:
       - type: "xtce"
         args:
           file: "mdb/xtce2.xml"

packet-viewer.yaml
~~~~~~~~~~~~~~~~~~

``packetPreprocessorClassName`` / ``packetPreprocessorArgs``
    Configure a packet pre-processor. Configuration options are identical to preprocessor configuration of a data link on Yamcs Server.

``fileFormats``
   List of supported file formats when opening a local packet dump file. The file format determines how to split the file in packets. Sub-keys:

   ``name``
      Name of the format, as visible in UI.
    
   ``packetInputStreamClassName`` / ``packetInputStreamArgs``
      Configures a packet input stream. Configuration options are identical to packet input stream configuration of a data link on Yamcs Server.
    
   ``rootContainer``
      Qualified name of the base container. Required if it cannot be uniquely determined.

Example:

.. code-block:: yaml

    packetPreprocessorClassName: org.yamcs.tctm.IssPacketPreprocessor
    fileFormats:
      - name: CCSDS Packets
        packetInputStreamClassName: org.yamcs.tctm.CcsdsPacketInputStream


Packet Filter
-------------

Packet Viewer includes a filter box for filtering the displayed packets through arbitrary expressions.

.. only:: latex or json or html

   .. Exclude this from manpage generation.

   .. image:: _images/packet-filter.png
      :align: center

For example, assume you have parameters ``/YSS/param1`` and ``/YSS/param2`` then you could write arbitrary expressions like:

.. code-block:: text

   param1 > 2
   param2 == 3
   param1 > 3 or param2 != 4

The **left-hand side** of a clause must always be the parameter. This may also be a fully qualified parameter name like ``/YSS/param1``.

The **operator** must be one of ``==``, ``!=``, ``<``, ``<=``, ``>``, ``>=`` or ``contains``. The latter is useful for string parameters.

The **right-hand side** of a clause may be a number or a string, and is compared to the engineering value of the parameter. The string may be surrounded by double quotes.

You can combine multiple clauses through the logical operators ``and``, ``or``, ``not`` (or ``&&``, ``||``, ``!``). Parentheses are allowed.

When done typing a filter, press :kbd:`ENTER` to apply it.

Filter on packet properties
~~~~~~~~~~~~~~~~~~~~~~~~~~~

There are two hardcoded "parameters" that allow filtering on the global packet name or length:

.. code-block:: text

   packet.name == DHS
   packet.length > 200

Filter on parameter presence
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The operator and right-hand side of a clause are optional. This allows filtering on the presence of a parameter inside a packet. Example:

.. code-block:: text

   param1

Or, display only packets that do *not* include a parameter ``param1``:

.. code-block:: text

   !(param1)

Filter grammar
~~~~~~~~~~~~~~

.. container:: productionlist

   .. productionlist:: packet-filter-grammar
      expr: `or_expr`
      or_expr: `and_expr` ( `or_op` `and_expr` )*
      and_expr: `unary_expr` ( `and_op` `unary_expr` )*
      unary_expr: `not_op` "(" `expr` ")"
                : | "(" `expr` ")"
                : | `comparison`
      comparison: `reference` [ `rel_op` `literal` ]
      reference: `refchar`+
      refchar: `letter` | `digit` | "/" | "_" | "-" | "[" | "]" | "."
      literal: `string` | `quoted_string`
      string: `stringchar`+
      quoted_string: '"' [ `string` ] '"'
      stringchar: `letter` | `digit` | ":" | "_" | "/" | "-"
      letter: "a"..."Z"
      digit: "0"..."9"
      rel_op: `eq_op` | `ne_op`
            : | `gt_op` | `lt_op`
            : | `ge_op` | `le_op`
            : | `matches_op` | "contains"
      eq_op: "eq" | "=="
      ne_op: "ne" | "!="
      gt_op: "gt" | ">"
      lt_op: "lt" | "<"
      ge_op: "ge" | ">="
      le_op: "le" | "<="
      matches_op: "matches" | "~"
      and_op: "and" | "&&"
      or_op: "or" | "||"
      not_op: "not" | "!"
