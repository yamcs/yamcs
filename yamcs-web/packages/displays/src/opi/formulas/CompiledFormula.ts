import * as ast from './ast';

export interface DataSourceStatus {
  value: any;
  acquisitionStatus: any;
}

export class CompiledFormula {
  // 'compiled' here really means 'parsed'.
  // The only reuse optimization is the AST.

  private parameterValues = new Map<string, DataSourceStatus>();

  constructor(private formula: ast.Formula) {
  }

  updateDataSource(parameter: string, status: DataSourceStatus) {
    this.parameterValues.set(parameter, status);
  }

  clearState() {
    this.parameterValues.clear();
  }

  getParameters(): string[] {
    const expression = this.formula.expression;
    const parameters: string[] = [];
    this.getExpressionParameters(expression, parameters);
    return parameters;
  }

  private getExpressionParameters(expression: ast.Expression, parameters: string[]) {
    switch (expression.type) {
      case 'ParameterLiteral':
        if (parameters.indexOf(expression.value) === -1) {
          parameters.push(expression.value);
        }
        break;
      case 'ConditionalExpression':
        this.getExpressionParameters(expression.consequent, parameters);
        if (expression.alternate) {
          this.getExpressionParameters(expression.alternate, parameters);
        }
        break;
      case 'BinaryExpression':
        this.getExpressionParameters(expression.left, parameters);
        this.getExpressionParameters(expression.right, parameters);
        break;
      case 'LogicalExpression':
        this.getExpressionParameters(expression.left, parameters);
        this.getExpressionParameters(expression.right, parameters);
        break;
      case 'UnaryExpression':
        this.getExpressionParameters(expression.argument, parameters);
        break;
      case 'CallExpression':
        for (const argument of expression.arguments) {
          this.getExpressionParameters(argument, parameters);
        }
        break;
    }
  }

  execute(): any {
    const expression = this.formula.expression;
    return this.executeExpression(expression);
  }

  private executeExpression(expression: ast.Expression): any {
    switch (expression.type) {
      case 'BooleanLiteral':
        return expression.value;
      case 'ParameterLiteral':
        const pval = this.parameterValues.get(expression.value);
        return pval ? pval.value : undefined;
      case 'ConstantLiteral':
        return expression.value;
      case 'NumericLiteral':
        return expression.value;
      case 'StringLiteral':
        return expression.value;
      case 'ConditionalExpression':
        return this.executeConditionalExpression(expression);
      case 'BinaryExpression':
        return this.executeBinaryExpression(expression);
      case 'LogicalExpression':
        return this.executeLogicalExpression(expression);
      case 'UnaryExpression':
        return this.executeUnaryExpression(expression);
      case 'CallExpression':
        return this.executeCallExpression(expression);
      default:
        throw new Error('Unexpected expression type');
    }
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
        return left === right;
      case '!=':
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

  private executeLogicalExpression(expression: ast.LogicalExpression): any {
    const left = this.executeExpression(expression.left);
    const right = this.executeExpression(expression.right);
    switch (expression.operator) {
      case '&&':
        return left && right;
      case '||':
        return left || right;
      default:
        throw new Error(`Unexpected logical operator ${expression.operator}`);
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
      default:
        throw new Error(`Unexpected unary operator ${expression.operator}`);
    }
  }

  private executeCallExpression(expression: ast.CallExpression): any {
    const symbol = expression.callee.name;
    const args: any[] = [];
    for (const a of expression.arguments) {
      args.push(this.executeExpression(a));
    }
    switch (symbol) {
      case 'abs':
        return Math.abs(args[0]);
      case 'acos':
        return Math.acos(args[0]);
      case 'asin':
        return Math.asin(args[0]);
      case 'atan':
        return Math.atan(args[0]);
      case 'cbrt':
        return Math.cbrt(args[0]);
      case 'ceil':
        return Math.ceil(args[0]);
      case 'cos':
        return Math.cos(args[0]);
      case 'cosh':
        return Math.cosh(args[0]);
      case 'exp':
        return Math.exp(args[0]);
      case 'floor':
        return Math.floor(args[0]);
      case 'log':
        return Math.log(args[0]);
      case 'log10':
        return Math.log10(args[0]);
      case 'signum':
        return Math.sign(args[0]);
      case 'sin':
        return Math.sin(args[0]);
      case 'sinh':
        return Math.sinh(args[0]);
      case 'sqrt':
        return Math.sqrt(args[0]);
      case 'tan':
        return Math.tan(args[0]);
      case 'tanh':
        return Math.tanh(args[0]);
      case 'toDegrees':
        return args[0] * 180 / Math.PI;
      case 'toRadians':
        return args[0] / 180 * Math.PI;
      case 'parameterAcquisitionStatus':
        return this.callParameterAcquisitionStatus(args[0]);
      default:
        throw new Error(`Unsupported function '${symbol}'`);
    }
  }

  private callParameterAcquisitionStatus(parameter: string) {
    const status = this.parameterValues.get(parameter);
    if (status) {
      return status.acquisitionStatus;
    }
  }
}
