Statements
==========

.. index:: ALTER SEQUENCE
   single: Statement; ALTER SEQUENCE

ALTER SEQUENCE Statement
------------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      alterSequenceStatement: "ALTER" "SEQUENCE" `objectName` "RESTART" [ "WITH" `restart` ]
      restart: `integer`

Changes the properties of an existing sequence generator.


.. index:: ALTER TABLE
   single: Statement; ALTER TABLE

ALTER TABLE Statement
---------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      alterTableStatement: "ALTER" "TABLE" `objectName` "RENAME" "TO" `objectName`

Changes table properties. Currently this is limited to renaming.


.. index:: CLOSE STREAM
   single: Statement; CLOSE STREAM

CLOSE STREAM Statement
----------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      closeStreamStatement: "CLOSE" "STREAM" `objectName`


.. index:: CREATE TABLE
   single: Statement; CREATE TABLE

CREATE TABLE Statement
----------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      createTableStatement: "CREATE" "TABLE" [ "IF" "NOT" "EXISTS" ] `tableName` "("
                          :     `tableColumnDefinition` ( "," `tableColumnDefinition` )*
                          :     "," "PRIMARY" "KEY" "(" `columnName` ( "," `columnName` )* ")"
                          :     [ "," "INDEX" "(" `columnName` ( "," `columnName` )* ")" ]
                          : ")"
                          : [ "HISTOGRAM" "(" `columnName` ( "," `columnName` )* ")" ]
                          : [ "ENGINE" `engineName` ]
                          : [ "PARTITION" "BY" `partitioningSpec` ]
                          : [ "TABLESPACE" `tablespaceName` ]
                          : [ "TABLE_FORMAT" "=" "COMPRESSED" ]
       tableColumnDefinition: `columnName` `dataType` [ "AUTO_INCREMENT" ]
       dataType: `simpleDataType` | `arrayDataType`
       arrayDataType: `simpleDataType` "[]"
       simpleDataType: : "BINARY"
                       : | "BOOLEAN"
                       : | "BYTE"
                       : | "DOUBLE"
                       : | "ENUM"
                       : | "HRES_TIMESTAMP"
                       : | "INT"
                       : | "LONG"
                       : | "PARAMETER_VALUE"
                       : | "SHORT"
                       : | "STRING"
                       : | "PROTOBUF" "(" `className` ")"
                       : | "TIMESTAMP"
                       : | "UUID"
       partitioningSpec: "TIME" "(" `columnName` [ "(" `timePartitioning` ")" ] ")"
                       : | "VALUE" "(" `columnName` ")"
                       : | "TIME_AND_VALUE" "("
                       :       `columnName` [ "(" `timePartitioning` ")" ],
                       :       `columnName`
                       :   ")"
       className: `string`
       columnName: `objectName`
       timePartitioning: "'YYYY'" | "'YYYY/DOY'" | "'YYYY/MM'"

.. rubric:: Partitioning

Partitioning allows to separate the data in different RocksDB databases (by time) and column families (by value).

Time partitioning allows the following schemes:

* ``YYYY``: one RocksDB database per year.
* ``YYYY/DOY``: one RocksDB database per combination year, and day of the year.
* ``YYYY/MM``: one RocksDB database per combination year, and month of the year.

Partitioning by time ensures that old data is frozen and not disturbed by new data coming in.


.. index:: CREATE STREAM
   single: Statement; CREATE STREAM

CREATE STREAM Statement
-----------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      createStreamStatement: "CREATE" "STREAM" `streamName` (
                           :     "AS" `streamExpression` [ "NOFOLLOW" ]
                           :     | "(" `streamColumnDefinition` ( "," `streamColumnDefinition` )* ")"
                           :  )
      streamExpression: `selectExpression` | `mergeExpression`
      streamColumnDefinition: `columnName` `dataType`


.. index:: DELETE
   single: Statement; DESCRIBE

DELETE Statement
----------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      deleteStatement: "DELETE" "FROM" `objectName`
                     : [ "WHERE" `expression` ]
                     : [ "LIMIT" `integer` ]

Delete records from a table.


.. index:: DESCRIBE
   single: Statement; DESCRIBE

DESCRIBE Statement
------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      describeStatement: "DESCRIBE" `objectName`

Obtain information about table or stream structure.


.. index:: DROP TABLE
   single: Statement; DROP TABLE

DROP TABLE Statement
--------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      dropTableStatement: "DROP" "TABLE" [ "IF" "EXISTS" ] `objectName`

Remove a table.


.. index:: INSERT
   single: Statement; INSERT

INSERT Statement
----------------

.. container:: productionlist

   .. productionlist:: sql-grammar   
      insertStatement: ( "INSERT" | "UPSERT" | "INSERT_APPEND" | "UPSERT_APPEND" | "LOAD" )
                     : "INTO" `objectName`
                     : (`streamExpression` | `insertValues`)
      insertValues: "(" `columnName` ( "," `columnName` )* "VALUES" "(" `selectList` ")"


.. index:: SELECT TABLE
   single: Statement; SELECT TABLE

SELECT TABLE Statement
----------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      selectTableStatement: "SELECT" `selectList`
                          : "FROM" `tupleSourceExpression`
                          : [ "[" `windowSpecification` "]" ]
                          : [ "WHERE" `expression` ]
                          : [ "ORDER" [ "ASC" | "DESC" ] ]
                          : [ "LIMIT" [ `offset` "," ] `rowCount` ]


.. index:: SHOW DATABASES
   single: Statement; SHOW DATABASES

SHOW DATABASES Statement
------------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      showDatabasesStatement: "SHOW" "DATABASES"

Lists the databases.


.. index:: SHOW ENGINES
   single: Statement; SHOW ENGINES

SHOW ENGINES Statement
----------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      showEnginesStatement: "SHOW" "ENGINES"

Lists the server's storage engines.


.. index:: SHOW SEQUENCES
   single: Statement; SHOW SEQUENCES

SHOW SEQUENCES Statement
------------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      showSequencesStatement: "SHOW" "SEQUENCES"

Lists the sequences in the current database.


.. index:: SHOW STREAMS
   single: Statement; SHOW STREAMS

SHOW STREAMS Statement
----------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      showStreamsStatement: "SHOW" "STREAMS"

Lists the streams in the current database.


.. index:: SHOW TABLES
   single: Statement; SHOW TABLES

SHOW TABLES Statement
---------------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      showTablesStatement: "SHOW" "TABLES"

Lists the tables in the current database.


.. index:: UPDATE
   single: Statement; UPDATE

UPDATE Statement
----------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      updateStatement: "UPDATE" "SET" `columnName` "=" `expression`
                     : ( "," `columnName` "=" `expression` )*
                     : [ "WHERE" `expression` ]
                     : [ "LIMIT" `integer` ]
