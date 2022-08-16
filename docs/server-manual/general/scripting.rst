Scripting in Yamcs
==================

Yamcs supports scripts in two different contexts:

1. as part of realtime loop - this is done using aglorithms defined in the MDB. Algorithms run in the JVM (Java Virtual Machine); the two supported script engines are  jython (java implementation of Python 2) and Nashorn (for javascript). More details about the definition of algorithms can be found here: :doc:`../../algorithm-definitions`. 
The reason for not running the algorithms into an external interpreter (which could then support Python 3) is becasue they have to run as fast as possible since they block the realtime processing while they run. See :doc:`../processors/tm-processing`. 
We recommended to keep the algorithms relatively simple - any big number crunching algorithm should be executed outside of the realtime loop.

1. the second way is via outside scripts using the Yamcs Python client (or any other client accessing the REST interface). 