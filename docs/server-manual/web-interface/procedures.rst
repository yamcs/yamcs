Procedures
==========

The Procedures group within the Yamcs web interface provides access to procedural functionality.

Run a script
------------

The "Run a script" page lets users execute predefined scripts. Scripts are stored under :file:`etc/scripts`.

Script files may be directly executable, or be associated to another program based on its file extension.

The default associations are:

.. list-table::
   :widths: 50 50
   :header-rows: 1

   * - Extension
     - Program
   * - java
     - java
   * - js
     - node
   * - mjs
     - node
   * - pl
     - perl
   * - py
     - python -u
   * - rb
     - ruby

Scripts can be selected from a drop-down. Arguments can be specified, in the format expected by the Script runtime.
Scripts can be run immediately or later. If later, they will appear on the Timeline.

Once started, the Script appears on the Activities page list. 
The Script Activity automatically marks itself successful or failed based on the script exitcode (0 for success).
If the script generates an output, it can be viewed by clicking on the Script Id on the Activities page.
