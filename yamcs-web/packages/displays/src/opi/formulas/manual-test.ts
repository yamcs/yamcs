import { FormulaCompiler } from './FormulaCompiler';

const compiler = new FormulaCompiler();

const script = compiler.compile(`=0.1*((44331.514- '/YSS/SIMULATOR/Altitude')/11880.516)^(1/0.1902632)`);

script.updateDataSource('/YSS/SIMULATOR/Altitude', {
  value: 1234,
  acquisitionStatus: 'good',
});

const parameters = script.getParameters();
console.log('haveparam', parameters);

const output = script.execute();
console.log('got output:', output);
