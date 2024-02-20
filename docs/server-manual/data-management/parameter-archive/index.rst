Parameter Archive
=================

.. toctree::
    :maxdepth: 1

    archive-filling
    internals

The Parameter Archive stores time ordered parameter values. The parameter archive is column oriented and is optimized for accessing a (relatively small) number of parameters over longer periods of time.

The Parameter Archive stores for each parameter tuples of (t\ :sub:`i`, ev\ :sub:`i`, rv\ :sub:`i`, ps\ :sub:`i`) where:

t\ :sub:`i`
    the *generation* timestamp of the value. The *reception* timestamp is not stored in the Parameter Archive.
ev\ :sub:`i`
    the engineering value of the parameter at the given time.
rv\ :sub:`i`
    the raw value of the parameter at the given time.
ps\ :sub:`i`
    the parameter status of the parameter at the given time.

The parameter status includes attributes such as out-of-limits indicators (alarms) and processing status. Yamcs Mission Database provides a mechanism through which a parameter can change its alarm ranges depending on the context. For this reason the Parameter Archive also stores the parameter status and the applicable alarm ranges at the given time.

In order to speed up the retrieval, the Parameter Archive stores data in segments of approximately 70 minutes. That means that all engineering values for one parameter for the 70 minutes are stored together; same for raw values, parameter status and timestamps.

Having all the data inside one segment of the same type offers possibility for good compression especially if the values do not change much or at all (as it is often the case).

While this structure is good for fast retrieval, it does not allow updating data very efficiently and in any case not in realtime. This is why the Parameter Archive is filled in batch mode. Data is accumulated in memory and flushed to disk periodically using different filling strategies.
