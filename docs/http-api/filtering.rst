Filtering
=========

Some list methods provide a ``filter`` option. This option can be use to provide a query expression to filter based on the fields of each list item. Methods providing this option allow to use ``POST`` in addition to ``GET``, to avoid encoding of lengthy queries in the query parameter.

The filter syntax allows for two kinds of search: text search, and field search.


Text Search
-----------

A single word is matched against the full resource. It is up to the specific resource implementation to determine which fields are considered for this comparison, usually all textual fields. The search is case-insensitive, exact, and may be partial.

For example, search resources that match the text `wombat`:

.. code-block:: text
   
   wombat

To find resources that match both the text `icy` and the text `wombat` (at the same time), provide them both separated by whitespace:

.. code-block:: text
   :caption: Separated by space

   icy wombat

.. code-block:: text
   :caption: Separated by newline

   icy
   wombat

Search terms may be enclosed in double quotes, which allows the search to include special characters.

The previous example is identical to:

.. code-block:: text

   "icy"
   "wombat"

If you would rather search for the exact sequence `icy wombat`, use double quotes around the full search term:

.. code-block:: text

   "icy wombat"

To search resources that `do not` match the text `wombat`, negate the term by prefixing with the minus sign:

.. code-block:: text

   -wombat


Logical Operators
^^^^^^^^^^^^^^^^^

Logical operators ``AND``, ``OR`` and ``NOT`` can be used to form more complicated queries. These operators must be specified in uppercase, else they are considered to be search terms.

For example:

.. code-block:: text

   wombat OR hippo

.. code-block:: text

   NOT hippo OR (icy AND wombat)

NOT has highest precedence, followed by OR, then AND. Where needed, use parenthesis to avoid any confusion.

Use of the `AND` operator is optional, as this is the default behavior when multiple terms are provided.


Line Comments
^^^^^^^^^^^^^

Queries can span any number of lines. Lines can be commented out using the `--` prefix.

The following example searches for resources that textually match with both `wombat` and `gorilla`.

.. code-block:: text

   wombat
   --hippo
   gorilla


Field Search
------------

Each filterable resource defines a number of fields that can be used to filter directly. This allows for better targeting than with text search, and will generally perform better.

The available filterable fields vary from one resource to another, and are documented on their respective pages (for example, see: :doc:`events/list-events`).

Field search requires a comparison query of the form: `FIELD OPERATOR VALUE`. For example, to filter resources that have the field `foo` set to `wombat`, use any of the following:

.. code-block:: text

   foo=wombat
   foo = wombat
   foo = "wombat"

Each field has a specified type: string, number, boolean, binary or enum. The following sections describe the operators for each of these types.


String Field Comparison Operators
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following operators can be used in string field comparisons.

.. list-table::
   :widths: 25 25 50

   * - =
     - foo = "wombat"
     - The field `foo` equals `wombat`
   * - !=
     - foo != "wombat"
     - The field `foo` does not equal `wombat`
   * - <
     - foo < "wombat"
     - The field `foo` is alphabetically before `wombat`
   * - <=
     - foo <= "wombat"
     - The field `foo` equals `wombat`, or is alphabetically before `wombat`
   * - >
     - foo > "wombat"
     - The field `foo` is alphabetically after `wombat`
   * - >=
     - foo >= "wombat"
     - The field `foo` equals `wombat`, or is alphabetically after `wombat`
   * - :
     - foo:"wombat"
     - The field `foo` contains the substring `wombat`
   * - =~
     - foo =~ "bat$"
     - The field `foo` ends with the substring `bat`
   * - !~
     - foo !~ "bat$"
     - The field `foo` does not end with the substring `bat`

The operators `=~` and `!~` allow to match the field against the provided regular expression. The match is unanchored, so use the prefix `^` and the suffix `$` when you want to match the full field value.

Regular expressions are case-sensitive. To enable case-insensitive matching, you can use an embedded flag expression:

.. code-block::

   foo =~ "(?i)bat$"

Regular expressions must be double-quoted. For the other operators, double quotes are optional, unless you want to match special characters.


Number Field Comparison Operators
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following operators can be used in number field comparisons:

.. list-table::
   :widths: 25 25 50

   * - =
     - foo = 123.45
     - The field `foo` equals `123.45`
   * - !=
     - foo != 123.45
     - The field `foo` does not equal `123.45`
   * - <
     - foo < 123.45
     - The field `foo` is smaller than `123.45`
   * - <=
     - foo <= 123.45
     - The field `foo` equals `123.45`, or is smaller than `123.45`
   * - >
     - foo > 123.45
     - The field `foo` is greater than `123.45`
   * - >=
     - foo >= 123.45
     - The field `foo` equals `123.45`, or is greater than `123.45`

The comparison value may be double-quoted.


Boolean Field Comparison Operators
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following operators can be used in boolean field comparisons:

.. list-table::
   :widths: 25 25 50

   * - =
     - foo = true
     - The field `foo` is `true`
   * - !=
     - foo != true
     - The field `foo` is not `true` (so, null or false)

The comparison values `true` and `false` are case-insensitive, and may be double-quoted.


Binary Field Comparison Operators
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following operators can be used in binary field comparisons:

.. list-table::
   :widths: 25 25 50

   * - =
     - foo = aabb
     - The field `foo` is two bytes long, `0xAA` and `0xBB`
   * - !=
     - foo != aabb
     - The field `foo` does not match `0xAABB`
   * - :
     - foo:aabb
     - The field `foo` contains the binary `0xAABB`

The provided hexstring is case-insensitive, and may be double-quoted.


Enum Field Comparison Operators
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Assume a field `foo` of the following enum type:

.. code-block:: java

   enum Severity {
     INFO,
     WATCH,
     WARNING,
     DISTRESS,
     CRITICAL,
     SEVERE;
   }

The following operators can be used in enum field comparisons.

.. list-table::
   :widths: 25 25 50

   * - =
     - foo = INFO
     - The field `foo` equals `INFO`
   * - !=
     - foo != INFO
     - The field `foo` does not equal `INFO`
   * - <
     - foo < WATCH
     - The field `foo` is before `WATCH`, using enum order
   * - <=
     - foo <= WATCH
     - The field `foo` is `WATCH`, or before `WATCH`, using enum order
   * - >
     - foo > WATCH
     - The field `foo` is after `WATCH`, using enum order
   * - >=
     - foo >= WATCH
     - The field `foo` is `WATCH`, or after `WATCH`, using enum order

The provided enum constant is case-insensitive, and may be double-quoted.
