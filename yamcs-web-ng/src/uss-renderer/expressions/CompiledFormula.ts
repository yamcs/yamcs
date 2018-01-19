import * as ast from './ast';

export class CompiledFormula {
  // 'compiled' here really means 'parsed'.
  // The only reuse optimization is the AST.

  assignments: {[key: string]: any} = {};

  constructor(private formula: ast.Formula) {
  }

  execute(): any {
    for (let i = 0; i < this.formula.body.length; i++) {
      const expression = this.formula.body[i].expression;
      const expressionResult = this.executeExpression(expression);
      if (i === this.formula.body.length - 1) {
        return expressionResult;
      }
    }
  }

  private executeExpression(expression: ast.Expression): any {
    switch (expression.type) {
      case 'Symbol':
        return this.assignments[expression.name];
      case 'BooleanLiteral':
        return expression.value;
      case 'NumericLiteral':
        return expression.value;
      case 'StringLiteral':
        return expression.value;
      case 'AssignmentExpression':
        return this.executeAssignmentExpression(expression);
      case 'ConditionalExpression':
        return this.executeConditionalExpression(expression);
      case 'BinaryExpression':
        return this.executeBinaryExpression(expression);
      case 'UnaryExpression':
        return this.executeUnaryExpression(expression);
      default:
        throw new Error(`Unexpected expression type ${expression.type}`);
    }
  }

  private executeAssignmentExpression(expression: ast.AssignmentExpression): any {
    const identifier = expression.left.name;
    const right = this.executeExpression(expression.right);
    this.assignments[identifier] = right;
    return right;
  }

  private executeBinaryExpression(expression: ast.BinaryExpression): any {
    const left = this.executeExpression(expression.left);
    const right = this.executeExpression(expression.right);
    switch (expression.operator) {
      case '+':
        return left + right;
      case '-':
        return left - right;
      case '*':
        return left * right;
      case '/':
        return left / right;
      case '^':
        return Math.pow(left, right);
      case '%':
        return left % right;
      case '==':
      case 'equals':
        return left === right;
      case '!=':
      case '<>':
        return left !== right;
      case '<=':
        return left <= right;
      case '<':
        return left < right;
      case '>=':
        return left >= right;
      case '>':
        return left > right;
      default:
        throw new Error(`Unexpected binary operator ${expression.operator}`);
    }
  }

  private executeConditionalExpression(expression: ast.ConditionalExpression): any {
    const test = this.executeExpression(expression.test);
    const consequent = this.executeExpression(expression.consequent);
    if (expression.alternate === undefined) {
      return test ? consequent : null;
    } else {
      const alternate = this.executeExpression(expression.alternate);
      return test ? consequent : alternate;
    }
  }

  private executeUnaryExpression(expression: ast.UnaryExpression): any {
    const argument = this.executeExpression(expression.argument);
    switch (expression.operator) {
      case '-':
        return -argument;
      case '+':
        return argument;
      case 'ABS':
        return Math.abs(argument);
      case '%':
        return argument / 100.0;
      case '²':
        return argument * argument;
      default:
        throw new Error(`Unexpected unary operator ${expression.operator}`);
    }
  }
}
