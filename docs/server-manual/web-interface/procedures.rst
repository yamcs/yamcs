Procedures
==========

The Procedures group within the Yamcs web interface provides access to procedural functionality.

Run a script
------------

The "Run a script" page lets users execute pre-defined scripts. Scripts are stored under "/opt/yamcs/etc/scripts".
Script files must start with the "#!/usr/bin/env" command to specify run-time environment they are written for.

Scripts can be selected from a drop-down. Arguments can be specified, in the format experted by the Script run-time.
Scripts can be ran immediately or later. If ran later, they will appear on the Timeline. 

Once started, the Script appears on the Activities page list. 
The Script Activity automatically marks itself successful or failed based on the script exitcode (0 for success).
If the script generates an output, it can be viewed by clicking on the Script Id on the Activities page.

