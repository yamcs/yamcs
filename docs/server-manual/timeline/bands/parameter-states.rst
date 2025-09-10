Parameter States
================

Band type for showing value changes of a Yamcs parameter.

For best results the displayed parameter should have a limited amount of possible values and/or change infrequently. Else the visualization will not be very useful, especially when zoomed out.

This generally means that enumeration, boolean or string parameters are the best candidates. Or a numeric parameter with custom value mapping.


Configuration Options
---------------------

Frozen
    If enabled, this band is always visible, even when scrolling down. Frozen bands always rendered above non-frozen bands.

Height
    Band height in pixels

Parameter
    Fully-qualified name of the parameter to be displayed


Value Mapping
-------------

You can apply a mapping of the parameter's values to a custom label. A mapping can be from a single discrete value to another, or it can also be a range mapping.

Mappings may also be used to influence the color selection for each value. Else the band will cycle through colors from a limited palette. The automated color selection algorithm will try to render the same values in the same color between different draw cycles, but this is only a best-effort algorithm.
