Expressions
===========

.. container:: productionlist

   .. productionlist:: sql-grammar
      simpleExpression: `additiveExpression` ( `bitwiseOp` `additiveExpression` )*
      additiveExpression: `multiplicativeExpression` ( `addOp` `multiplicativeExpression` )*
      multiplicativeExpression: `exponentExpression` ( `multOp` `multiplicativeExpression` )*
      exponentExpression: `unaryExpression` [ "**" `unaryExpression` ]
      unaryExpression: [ "+" | "-" ] `primaryExpression`
      primaryExpression: `integer`
                       : | `float`
                       : | `string`
                       : | "?"
                       : | "(" `simpleExpression` ")"
                       : | "ARRAY" "[" `expressionList` "]"
                       : | `functionCall`
                       : | `objectName`
      expression: `andExpression` ( "OR" `andExpression` )*
      andExpression: `unaryLogicalExpression`
                   : | "(" `expression` ")" ( "AND" (
                   :     `unaryLogicalExpression`
                   :     | "(" `expression` ")"
                   :   ) )*
      unaryLogicalExpression: [ "NOT" ] `relationalExpression`
      relationalExpression: `simpleExpression`
                          : [
                          :     `relOp` `simpleExpression`
                          :     | `inClause`
                          :     | `betweenClause`
                          :     | `likeClause`
                          :     | `isNullClause`
                          : ]
      expressionList: `expression` ( "," `expression` )*
      inClause: [ "NOT" ] "IN" "(" `expressionList` ")"
      betweenClause: [ "NOT" ] "BETWEEN" `simpleExpression` "AND" `simpleExpression`
      likeClause: [ "NOT" ] "LIKE" ( STRING | "?" )
      isNullClause: "IS" [ "NOT" ] "NULL"
      functionCall: `objectName` "(" [ `expressionList` | "*" ] ")"

.. container:: productionlist

   .. productionlist:: sql-grammar
      selectExpression: "SELECT" `selectList`
                      : "FROM" `tupleSourceExpression`
                      : [ "[" `windowSpecification` "]" ]
                      : [ "WHERE" `expression` ]
                      : [ "ORDER" [ "ASC" | "DESC" ] ]
                      : [ "LIMIT" [ `offset` "," ] `rowCount` ]
      mergeExpression: "MERGE" `tupleSourceExpression` ( "," `tupleSourceExpression` )*
                     : "USING" `columnName`
                     : [ "ORDER" [ "ASC" | "DESC" ] ]
                     : [ "LIMIT" [ `offset` "," ] `rowCount` ]
      selectList: `selectItem` ( "," `selectItem` )*
      selectItem: "*"
                : | `simpleExpression` [ [ "AS" ] `columnName` ]
      tupleSourceExpression: `objectName` [ "HISTOGRAM" "(" `columnName` [ "," `mergeTime` ] ")" ]
                           : | "(" `streamExpression` ")"
      windowSpecification: "SIZE" `integer` "ADVANCE" `integer` `windowMode`
      windowMode: "TIME" | "TUPLES" | "ON" `columnName`
      offset: `integer`
      rowCount: `integer`
      mergeTime: `integer`
