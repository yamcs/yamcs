export type Expression = BinaryExpression
  | CallExpression
  | ConditionalExpression
  | LogicalExpression
  | UnaryExpression
  | Literal
  | Symbol;

export type Literal = BooleanLiteral
  | ConstantLiteral
  | ParameterLiteral
  | NumericLiteral
  | StringLiteral;

export interface Formula {
  type: 'Formula';
  expression: Expression;
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
  operator: '+' | '-';
  argument: Expression;
  prefix: boolean;
}

export interface BooleanLiteral {
  type: 'BooleanLiteral';
  value: boolean;
}

export interface ConstantLiteral {
  type: 'ConstantLiteral';
  value: number;
}

export interface ParameterLiteral {
  type: 'ParameterLiteral';
  value: string;
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
