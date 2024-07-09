Alarm Definitions
=================

Yamcs supports the XTCE notion of *alarms*. Based on the value of a parameter, Yamcs assigns a monitoring result to each parameter.

An alarm check is performed when any of these applies (in order):

* The condition for a context alarm is satisfied (if multiple, the alarm specification for first matching context is applied).
* There is an alarm specification without a context (default alarm).

The monitoring result can be:

* *null* (no alarm specification applies)
* IN_LIMITS (an alarm was checked, but the value is within limits)
* WATCH
* WARNING
* DISTRESS
* CRITICAL
* SEVERE
