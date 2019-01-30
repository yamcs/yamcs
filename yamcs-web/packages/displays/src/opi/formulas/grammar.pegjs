{
  function extractList(list: any, index: any) {
    return list.map(function(element: any) { return element[index]; });
  }

  function extractOptional(optional: any, index: any) {
    return optional ? optional[index] : null;
  }

  function buildList(head: any, tail: any, index: any) {
    return [head].concat(extractList(tail, index));
  }

  function buildBinaryExpression(head: any, tail: any) {
    return tail.reduce(function(result: any, element: any) {
      return {
        type: 'BinaryExpression',
        operator: element[1],
        left: result,
        right: element[3]
      };
    }, head);
  }

  function buildLogicalExpression(head: any, tail: any) {
    return tail.reduce(function(result: any, element: any) {
      return {
        type: 'LogicalExpression',
        operator: element[1],
        left: result,
        right: element[3]
      };
    }, head);
  }

  function optionalList(value: any) {
    return value !== null ? value : [];
  }
}

Start
  = '=' _ formula:Formula _ { return formula; }

Formula
  = expression:Expression {
    return {
      type: 'Formula',
      expression: expression
    };
  }

_
  = WhiteSpace*

WhiteSpace
  = ' '
  / '\t'

FALSE = 'false'i
TRUE = 'true'i
PI = 'PI'i
E = 'E'i

ReservedWord
  = BooleanLiteral
  / ConstantLiteral

Identifier
  = !ReservedWord symbol:IdentifierSymbol {
    return symbol;
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
  / ConstantLiteral
  / NumericLiteral
  / StringLiteral
  / ParameterLiteral

BooleanLiteral 'boolean'
  = TRUE { return { type: 'BooleanLiteral', value: true }; }
  / FALSE { return { type: 'BooleanLiteral', value: false }; }

ConstantLiteral 'constant'
  = PI { return { type: 'ConstantLiteral', value: Math.PI }; }
  / E { return { type: 'ConstantLiteral', value: Math.E }; }

NumericLiteral 'number'
  = DecimalIntegerLiteral '.' DecimalDigit* {
    return { type: 'NumericLiteral', value: parseFloat(text()) };
  }
  / '.' DecimalDigit+ {
    return { type: 'NumericLiteral', value: parseFloat(text()) };
  }
  / DecimalIntegerLiteral {
    return { type: 'NumericLiteral', value: parseFloat(text()) };
  }

DecimalIntegerLiteral
  = '0'
  / NonZeroDigit DecimalDigit*

DecimalDigit
  = [0-9]

NonZeroDigit
  = [1-9]

StringLiteral 'string'
  = '"' chars:[^\n\r\f"]* '"' {
    return { type: 'StringLiteral', value: chars.join('') };
  }

ParameterLiteral 'parameter'
  = '\'' chars:[^\n\r\f']* '\'' {
    return { type: 'ParameterLiteral', value: chars.join('') };
  }

PrimaryExpression
  = Literal
  / '(' _ expression:Expression _ ')' { return expression; }

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
  = head:CallExpression tail:(_ ',' _ CallExpression)* {
    return buildList(head, tail, 3);
  }

MultiplicativeExpression
  = head:CallExpression tail:(_ ('*' / '/' / '^' / '%') _ CallExpression)* {
      return buildBinaryExpression(head, tail);
    }

AdditiveExpression
  = head:MultiplicativeExpression tail:(_ ('+' / '-') _ MultiplicativeExpression)* {
      return buildBinaryExpression(head, tail);
    }

Expression
  = AdditiveExpression
