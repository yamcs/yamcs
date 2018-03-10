import { FormulaCompiler } from './expressions/FormulaCompiler';
import { CompiledFormula, DataSourceStatus } from './expressions/CompiledFormula';
import { DataSourceBinding } from './DataSourceBinding';

export interface ComputationArgument {
  opsName?: string;
  pathName?: string;
  sid?: string;
}

export class ComputationBinding extends DataSourceBinding {

  static readonly TYPE = 'COMPUTATION';

  expression: string;
  args: ComputationArgument[] = [];

  engine: CompiledFormula;

  constructor() {
    super(ComputationBinding.TYPE);
  }

  compileExpression() {
    const compiler = new FormulaCompiler();
    try {
      this.engine = compiler.compile(this.expression);
    } catch (err) {
      // tslint:disable-next-line:no-console
      console.error('Cannot compile expression', err, this.expression);
      return;
    }

    for (const arg of this.args) {
      if (arg.pathName && arg.opsName) {
        this.engine.registerDataSourceMapping(arg.pathName, arg.opsName);
      }
    }
  }

  updateDataSource(opsName: string, status: DataSourceStatus) {
    if (this.engine) {
      this.engine.updateDataSource(opsName, status);
    }
  }

  executeExpression() {
    if (this.engine) {
      return this.engine.execute();
    }
  }

  toString() {
    return `[${this.dynamicProperty}] ${this.expression}`;
  }
}
