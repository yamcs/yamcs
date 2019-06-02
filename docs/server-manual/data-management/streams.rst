Streams
=======

The concept of *streams* was inspired from the domain of Complex Event Processing (CEP) or Stream Processing. Streams are similar to database tables, but represent continuously moving data. SQL-like statements can be defined on streams for filtering, aggregation, merging or other operations. Yamcs uses streams for distributing data between all components running inside the same JVM.

Typically there is a stream for realtime telemetry called ``tm_realtime``, one for realtime processed parameters called ``pp_realtime``, one for commands called ``tc``, etc.
