{

  function extractList(list, index) {
    return list.map(function(element) { return element[index]; });
  }

  function extractOptional(optional, index) {
    return optional ? optional[index] : null;
  }

  function buildList(head, tail, index) {
    return [head].concat(extractList(tail, index));
  }

  function buildBinaryExpression(head, tail) {
    return tail.reduce(function(result, element) {
      return {
        type: 'BinaryExpression',
        operator: element[1],
        left: result,
        right: element[3]
      };
    }, head);
  }

  function buildLogicalExpression(head, tail) {
    return tail.reduce(function(result, element) {
      return {
        type: 'LogicalExpression',
        operator: element[1],
        left: result,
        right: element[3]
      };
    }, head);
  }

  function optionalList(value) {
    return value !== null ? value : [];
  }
}

Start
  = _ formula:Formula _ { return formula; }

Formula
  = head:ExpressionStatement tail:(_ ExpressionStatement)* {
    return {
      type: 'Formula',
      body: buildList(head, tail, 1)
    };
  }

ExpressionStatement
  = expression:Expression EOS {
    return {
      type: 'ExpressionStatement',
      expression: expression
    }
  }

_
  = WhiteSpace*

__
  = (WhiteSpace / LineTerminatorSequence)*

WhiteSpace
  = ' '
  / '\t'

LineTerminatorSequence 'end of line'
  = '\n'
  / '\r\n'
  / '\r'

EOS
  = _ LineTerminatorSequence
  / _ ';'
  / __ EOF

EOF
  = !.

AND = 'and'i
ELSE = 'else'i
EQUALS = 'equals'i
FALSE = 'false'i
IF = 'if'i
IN = 'in'i
THEN = 'then'i
NOT = 'not'i
OR = 'or'i
TRUE = 'true'i
XOR = 'xor'i

ReservedWord
  = BooleanLiteral
  / AND
  / ELSE
  / EQUALS
  / IF
  / IN
  / THEN
  / NOT
  / OR
  / TRUE
  / XOR

Identifier
  = !ReservedWord symbol:IdentifierSymbol {
    return { symbol };
  };

IdentifierSymbol 'identifier'
  = head:IdentifierStart tail:IdentifierPart* {
    return {
      type: 'Symbol',
      name: head + tail.join('')
    };
  }

IdentifierStart
  = [a-zA-Z]
  / '_'

IdentifierPart
  = [a-zA-Z0-9_]

Literal
  = BooleanLiteral
  / NumericLiteral
  / StringLiteral

BooleanLiteral 'boolean'
  = TRUE { return { type: 'BooleanLiteral', value: true }; }
  / FALSE { return { type: 'BooleanLiteral', value: false }; }

NumericLiteral 'integer'
  = [0-9]+ { return { type: 'NumericLiteral', value: parseInt(text(), 10) }; }

StringLiteral 'string'
  = '"' chars:[^\n\r\f\\"]* '"' {
    return { type: 'StringLiteral', value: chars.join('') };
  }
  / '\'' chars:[^\n\r\f\\']* '\'' {
    return { type: 'StringLiteral', value: chars.join('') };
  }

PrimaryExpression
  = Identifier
  / Literal
  // / ArrayLiteral
  / '(' _ expression:Expression _ ')' { return expression; }
  / '[' _ argument:Expression _ ']' {
    return {
      type: 'UnaryExpression',
      operator: 'ABS',
      argument: argument,
    };
  }

UnaryExpression
  = PrimaryExpression
  / operator:UnaryOperator _ argument:UnaryExpression _ {
    return {
      type: 'UnaryExpression',
      operator: operator,
      argument: argument,
    };
  }

UnaryOperator
  = '-'
  / '+'

CallExpression
  = callee:Identifier _ args:Arguments {
    return {
      type: 'CallExpression',
      callee: callee,
      arguments: args
    };
  }
  / UnaryExpression

Arguments
  = '(' _ args:(ArgumentList _)? ')' {
    return optionalList(extractOptional(args, 0));
  }

ArgumentList
  = head:UnaryExpression tail:(_ ',' _ UnaryExpression)* {
    return buildList(head, tail, 3);
  }

PostfixExpression
  = argument:CallExpression operator:PostfixOperator {
    return {
      type: 'UnaryExpression',
      operator: operator,
      argument: argument
    };
  }
  / CallExpression

PostfixOperator
  = $('%' !Literal) // When followed by literal, assume modulo instead
  / '²'

MultiplicativeExpression
  = head:PostfixExpression tail:(_ ('*' / '/' / '^' / '%') _ PostfixExpression)* {
      return buildBinaryExpression(head, tail);
    }

AdditiveExpression
  = head:MultiplicativeExpression tail:(_ ('+' / '-') _ MultiplicativeExpression)* {
      return buildBinaryExpression(head, tail);
    }

RelationalExpression
  = head:AdditiveExpression tail:(_ RelationalOperator _ AdditiveExpression)* {
    return buildBinaryExpression(head, tail);
  }

RelationalOperator
  = '<='
  / '>='
  / '<'
  / '>'

EqualityExpression
  = head:RelationalExpression tail:(_ EqualityOperator _ RelationalExpression)* {
    return buildBinaryExpression(head, tail);
  }

EqualityOperator
  = '=='
  / EQUALS
  / '!='
  / '<>'

BitwiseANDExpression
  = head:EqualityExpression tail:(_ BitwiseANDOperator _ EqualityExpression)* {
     return buildBinaryExpression(head, tail);
  }

BitwiseANDOperator
  = $('&' ![&])

BitwiseXORExpression
  = head:BitwiseANDExpression tail:(_ BitwiseXOROperator _ BitwiseANDExpression)* {
     return buildBinaryExpression(head, tail);
  }

BitwiseXOROperator
  = '~'
  / XOR

BitwiseORExpression
  = head:BitwiseXORExpression tail:(_ BitwiseOROperator _ BitwiseXORExpression)* {
    return buildBinaryExpression(head, tail);
  }

BitwiseOROperator
  = $('|' ![|])

LogicalANDExpression
  = head:BitwiseORExpression tail:(_ LogicalANDOperator _ BitwiseORExpression)* {
    return buildLogicalExpression(head, tail);
  }

LogicalANDOperator
  = '&&'
  / AND

LogicalORExpression
  = head:LogicalANDExpression tail:(_ LogicalOROperator _ LogicalANDExpression)* {
    return buildLogicalExpression(head, tail);
  }

LogicalOROperator
  = '||'
  / OR

ConditionalExpression
  = IF _ test:LogicalORExpression _ THEN _ consequent:AssignmentExpression _ ELSE _ alternate:AssignmentExpression {
      return {
        type: 'ConditionalExpression',
        test: test,
        consequent: consequent,
        alternate: alternate
      };
  }
  / IF _ test:LogicalORExpression _ THEN _ consequent:AssignmentExpression {
    return {
      type: 'ConditionalExpression',
      test: test,
      consequent: consequent,
      alternate: undefined
    };
  }
  / LogicalORExpression


AssignmentExpression
  = left:Identifier _ '=' !'=' _ right:AssignmentExpression {
    return {
      type: 'AssignmentExpression',
      left: left,
      right: right
    };
  }
  / ConditionalExpression

Expression
  = AssignmentExpression
