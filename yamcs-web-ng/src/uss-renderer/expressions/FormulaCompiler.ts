import * as ast from './ast';
import { CompiledFormula } from './CompiledFormula';

const parser = require('./parser.js');

export class FormulaCompiler {

  compile(formulaString: string): CompiledFormula {
    const formula = parser.parse(formulaString) as ast.Formula;

    console.log(JSON.stringify(formula, null, 2));

    return new CompiledFormula(formula);
  }

  execute(formulaString: string): any {
    const compiledFormula = this.compile(formulaString);
    return compiledFormula.execute();
  }
}
