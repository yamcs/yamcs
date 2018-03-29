import * as ast from './ast';
import { CompiledFormula } from './CompiledFormula';

import { parse } from './parser';

export class FormulaCompiler {

  compile(formulaString: string) {
    const formula = parse(formulaString, {}) as ast.Formula;
    // console.log(JSON.stringify(formula, null, 2));
    return new CompiledFormula(formula);
  }

  execute(formulaString: string) {
    const compiledFormula = this.compile(formulaString);
    return compiledFormula.execute();
  }
}
