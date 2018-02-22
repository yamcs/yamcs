import { FormulaCompiler } from './FormulaCompiler';

const compiler = new FormulaCompiler();

const script = compiler.compile("if parameterValue(\"\\\\User_Selected_Data_Dump_Status\") == \"Yes\" then \"blue\" else \"black\"");


script.registerDataSourceMapping('//a/path', 'AN_OPS_NAME');
script.updateDataSource('AN_OPS_NAME', {
  value: 'the value',
  acquisitionStatus: 'good',
});

const output = script.execute();
console.log('got output:', output);
