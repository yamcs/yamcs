Algorithms
==========

Yamcs supports the XTCE notion of *algorithms*. Algorithms are user scripts that can perform arbitrary logic on a set of incoming parameters. The result is typically one or more derived parameters, called *output parameters*, that are delivered together with the original set of parameters (at least, if they have been subscribed to).

Output parameters are very much identical to regular parameters. They can be calibrated (in which case the algorithm's direct outcome is considered the raw value), and they can also be subject to alarm generation.

Algorithms can be written in any JSR-223 scripting language. The preferred language is specified in the instance configuration file, and applies to all algorithms within that instance. By default Yamcs ships with support for JavaScript algorithms since the standard Oracle Java distribution contains the Nashorn JavaScript engine. Support for other languages (e.g. Python) requires installing additional dependencies.

Yamcs will bind these input parameters in the script's execution context, so that they can be accessed from within there. In particular the following attributes are made available:

value
    the engineering value
rawValue
    the raw value (if the parameter has a raw value)
monitoringResult
    the result of the monitoring: *null*, ``DISABLED``, ``WATCH``, ``WARNING``, ``DISTRESS``, ``CRITICAL`` or ``SEVERE``.
rangeCondition
    If set, one of ``LOW`` or ``HIGH``.

If there was no update for a certain parameter, yet the algorithm is still being executed, the previous value of that parameter will be retained.


Triggers
--------

Algorithms can trigger on two conditions:

#. Whenever a specified parameter is updated
#. Periodically (expressed in milliseconds)

Multiple triggers can be combined. In the typical example, an algorithm will trigger on updates for each of its input parameters. In other cases (for example because the algorithm doesn't have any inputs), it may be necessary to trigger on some other parameter. Or maybe a piece of logic just needs to be run at regular time intervals, rather than with each parameter update.

If an algorithm was triggered and not all of its input parameters were set, these parameters *will* be defined in the algorithm's scope, but with their value set to ``null``.


User Libraries
--------------

The Yamcs algorithm engine can be configured to import a number of user libraries. Just like with algorithms, these libraries can contain any sort of logic and are written in the same scripting language. Yamcs will load user libraries *one time only* at start-up in their defined order. This will happen before running any algorithm. Anything that was defined in the user library, will be accessible by any algorithm. In other words, user libraries define a kind-of global scope. Common use cases for libraries are: sharing functions between algorithms, shortening user algorithms, easier outside testing of algorithm logic, ...

Allowing to split the code in different user libraries is merely a user convenience. From the server perspective they could all be merged together in one big file.


Algorithm Scope
---------------

User algorithms have each their own scope. This scope is safe with respect to other algorithms (i.e. variables defined in algorithm *a* will not leak to algorithm *b*.

An algorithm's scope, however, is shared across multiple algorithm runs. This allows you to keep variables inside internal memory if needed. Do take caution with initializing your variables correctly at the beginning of your algorithm if you only update them under a certain set of conditions (unless of course you intend them to keep their value across runs).


Sharing State
-------------

If some kind of a shared state is required between multiple algorithms, the user libraries' shared scope could be used for this. In many cases, the better solution would be to just output a parameter from one algorithm, and input it into another. Yamcs will automatically detect such dependencies, and will execute algorithms in the correct order.


Historic Values
---------------

With what has been described so far, it would already be possible to store values in an algorithm's scope and perform windowing operations, such as averages. Yamcs goes a step further by allowing you to input a particular *instance* of a parameter. By default instance *0* is inputted, which means the parameter's actual value. But you could also define instance *-1* for inputting the parameter's value as it was on the previous parameter update. If you define input parameters for, say, each of the instances *-4*, *-3*, *-2*, *-1* and *0*, your user algorithm could be just a simple oneliner, since Yamcs is taking care of the administration.

Algorithms with windowed parameters will only trigger as soon as each of these parameters have all instances defined (i.e. when the windows are full).
