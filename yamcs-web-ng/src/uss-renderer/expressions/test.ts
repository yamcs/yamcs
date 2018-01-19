import { FormulaCompiler } from './FormulaCompiler';

const compiler = new FormulaCompiler();

const output = compiler.execute('ab = 50%; if parameterValue("param") then "blue"');
console.log('got output:', output);
