export type Expression = AssignmentExpression
  | BinaryExpression
  | CallExpression
  | ConditionalExpression
  | LogicalExpression
  | UnaryExpression
  | Literal
  | Symbol;

export type Literal = BooleanLiteral
  | NumericLiteral
  | StringLiteral;

export interface Formula {
  type: 'Formula';
  body: ExpressionStatement[];
}

export interface ExpressionStatement {
  type: 'ExpressionStatement';
  expression: Expression;
}

export interface AssignmentExpression {
  type: 'AssignmentExpression';
  left: Symbol;
  right: Expression;
}

export interface BinaryExpression {
  type: 'BinaryExpression';
  operator: '+' | '-' | '*' | '/' | '^' | '%' | '==' | 'equals' | '!=' | '<>' | '<=' | '>=' | '<' | '>';
  left: Expression;
  right: Expression;
}

export interface CallExpression {
  type: 'CallExpression';
  callee: Symbol;
  arguments: Expression[];
}

export interface ConditionalExpression {
  type: 'ConditionalExpression';
  operator: string;
  test: Expression;
  consequent: Expression;
  alternate?: Expression;
}

export interface LogicalExpression {
  type: 'LogicalExpression';
  operator: string;
  left: Expression;
  right: Expression;
}

export interface UnaryExpression {
  type: 'UnaryExpression';
  operator: '+' | '-' | 'ABS' | '%' | '²';
  argument: Expression;
  prefix: boolean;
}

export interface BooleanLiteral {
  type: 'BooleanLiteral';
  value: boolean;
}

export interface NumericLiteral {
  type: 'NumericLiteral';
  value: number;
}

export interface StringLiteral {
  type: 'StringLiteral';
  value: string;
}

export interface Symbol {
  type: 'Symbol';
  name: string;
}
