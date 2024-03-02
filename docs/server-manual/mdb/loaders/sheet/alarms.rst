Alarms Sheet
============

This sheet defines how the monitoring results of a parameter should be derived. E.g. if a parameter exceeds some pre-defined value, this parameter's state changes to ``CRITICAL``.

``parameter reference``
    The reference name of the parameter for which this alarm definition applies

``context``
    A condition under which the defined triggers apply. This can be used to define multiple different sets of triggers for one and the same parameter, that apply depending on some other condition (typically a state of some kind). When left blank, the defined set of conditions are assumed to be part of the *default* context.

    Contextual alarms are evaluated from top to bottom, until a match is found. If no context conditions apply, the default context applies.

``report``
    When alarms under the given context should be reported. Should be one of ``OnSeverityChange`` or ``OnValueChange``. With ``OnSeverityChange`` being the default. The condition ``OnValueChange`` will check value changes based on the engineering values. It can also be applied to a parameter without any defined severity levels, in which case an event will be generated with every change in value.

``min violations``
    Number of successive instances that meet any of the alarm conditions under the given context before the alarm event triggers (defaults to 1). This field affects when an event is generated (i.e. only after X violations). It does not affect the monitoring result associated with each parameter. That would still be out of limits, even after a first violation.

``watch: trigger type``
    One of ``low`` (or alias ``lowInclusive``), ``high`` (or alias ``highInclusive``), ``lowExclusive``, ``highExlusive`` or ``state``. For each context of a numeric parameter, you can have both a low and a high trigger that lead to the ``WATCH`` state. For each context of an enumerated parameter, you can have multiple state triggers that lead to the ``WATCH`` state.

``watch: trigger value``
    If the trigger type is ``low``, ``lowInclusive``, ``high`` or ``highInclusive``: a numeric value indicating the low resp. high limit value. The value is considered inclusive with respect to its nominal range. For example, a low limit of 20, will have a ``WATCH`` alarm if and only if its value is smaller than 20.

    If the trigger type is ``lowExclusive`` or ``highExclusive``: a numeric value indicating the low resp. heigh limit value. The value is considered exclusive with respect to its nominal range. For example, a lowExclusive limit of 20, will have a ``WATCH`` alarm if and only if its value is smaller than or equal to 20.

    If the trigger value is ``state``: a state that would bring the given parameter in its ``WATCH`` state.

``warning trigger type``, ``warning trigger value``
    Analogous to ``watch`` trigger

``distress trigger type``, ``distress trigger value``
    Analogous to ``watch`` trigger

``critical trigger type``, ``critical trigger value``
    Analogous to ``watch`` trigger

``severe trigger type``, ``severe trigger value``
    Analogous to ``watch`` trigger
