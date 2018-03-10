import * as ast from './ast';

export interface DataSourceStatus {
  value: any;
  acquisitionStatus: any;
}

export class CompiledFormula {
  // 'compiled' here really means 'parsed'.
  // The only reuse optimization is the AST.

  private assignments = new Map<string, any>();

  // Path names are used as argument to some functions
  // This table allows converting from an opsname.
  private pathname2opsname = new Map<string, string>();

  private dataSourceStatusByOpsName = new Map<string, DataSourceStatus>();

  constructor(private formula: ast.Formula) {
  }

  registerDataSourceMapping(pathName: string, opsName: string) {
    this.pathname2opsname.set(pathName, opsName);
  }

  updateDataSource(opsName: string, status: DataSourceStatus) {
    this.assignments.set(opsName, status.value);
    this.dataSourceStatusByOpsName.set(opsName, status);
  }

  clearState() {
    this.assignments.clear();
    this.dataSourceStatusByOpsName.clear();
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
        return this.assignments.get(expression.name);
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

  private executeAssignmentExpression(expression: ast.AssignmentExpression): any {
    const identifier = expression.left.name;
    const right = this.executeExpression(expression.right);
    this.assignments.set(identifier, right);
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

  private executeLogicalExpression(expression: ast.LogicalExpression): any {
    const left = this.executeExpression(expression.left);
    const right = this.executeExpression(expression.right);
    switch (expression.operator) {
      case '&&':
      case 'and':
        return left && right;
      case '||':
      case 'or':
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

  private executeCallExpression(expression: ast.CallExpression): any {
    const symbol = expression.callee.name;
    const args: any[] = [];
    for (const a of expression.arguments) {
      args.push(this.executeExpression(a));
    }
    switch (symbol) {
      case 'print':
        // tslint:disable-next-line:no-console
        console.log(...args);
        break;
      case 'parameterValue':
        return this.callParameterValue(args[0]);
      case 'parameterAcquisitionStatus':
        return this.callParameterAcquisitionStatus(args[0]);
      default:
        throw new Error(`Unsupported function '${symbol}'`);
    }
  }

  private callParameterValue(pathName: string) {
    const opsName = this.pathname2opsname.get(pathName);
    if (opsName) {
      return this.assignments.get(opsName);
    }
  }

  private callParameterAcquisitionStatus(pathName: string) {
    const opsName = this.pathname2opsname.get(pathName);
    if (opsName) {
      const status = this.dataSourceStatusByOpsName.get(opsName);
      if (status) {
        return status.acquisitionStatus;
      }
    }
  }
}
