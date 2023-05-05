Literals
========

Integer Literals
----------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      integer: `decinteger` | `hexinteger`
      decinteger: digit+
      hexinteger: "0" "X" `hexdigit`+
      hexdigit: `digit` | "A"..."F"
      digit: "0"..."9"


Float Literals
--------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      float: `digit`* "." `digit`+ [ `exponent` ]
           : | `digit`+ `exponent`
      exponent: [ "+" | "-" ] [ "E" ] `digit`+


String Literals
---------------

.. container:: productionlist

   .. productionlist:: sql-grammar
      string: "'" stringchar*  "'" ( "'" stringchar* "'" )*
      stringchar: <any character except newline or quote>

.. rubric:: Concatenation

Adjacent string literals (delimited by whitespace) are allowed, and concatenated at compile time.
