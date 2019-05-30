Alarms
======

Yamcs supports the XTCE notion of *alarms*. Based on the value of a parameter, Yamcs assigns a monitoring result to each parameter. The default monitoring result is DISABLED.

For enumerated parameters, the monitoring result can be:

* *null* (no alarm states are defined for this parameter)
* DISABLED (no alarms are applicable given the current set of updated parameter values)
* IN_LIMITS (an alarm was checked, but the value is within limits)
* WATCH
* WARNING
* DISTRESS
* CRITICAL
* SEVERE

For numeric parameters, the monitoring result can be:

* *null* (no alarm ranges are defined for this parameter)
* DISABLED (no alarms are applicable given the current set of updated parameter values)
* IN_LIMITS (an alarm was checked, but the value is within limits)
* WATCH
* WARNING
* DISTRESS
* CRITICAL
* SEVERE

Numeric parameter values get also tagged with a range condition LOW or HIGH, indicating whether the parameter value is too low or too high.

As part of the Mission Database definition, each parameter can have conditions by which the monitoring result should be set to a certain value. If the alarm conditions for multiple severity levels match, the highest severity level will always win.

For each parameter, multiple different sets of alarm conditions can be defined. A *context* condition is used to determine which set is applicable (for example, apply a different set of alarms if some other parameter is set to 'CONTINGENCY MODE').
