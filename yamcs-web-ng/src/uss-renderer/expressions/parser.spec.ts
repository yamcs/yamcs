/**
 * Examples:
 *
 * if parameterValue("\\EMMI_1_Emergency_Stop_Run_Time_BIT") =="Danger" then "red" else "black";
 */
import { FormulaCompiler } from './FormulaCompiler';

const c = new FormulaCompiler();

describe('parser', () => {

  it('arithmetics', () => {
    expect(c.execute('5')).toBe(5);
    expect(c.execute('-5')).toBe(-5);
    expect(c.execute('5+3')).toBe(8);
    expect(c.execute('5-3')).toBe(2);
    expect(c.execute('5*3')).toBe(15);
    expect(c.execute('6/3')).toBe(2);
    expect(c.execute('(1 * 5)+ (2+3)')).toBe(10);
    expect(c.execute('2^3')).toBe(8);
    expect(c.execute('10%3')).toBe(1);
    expect(c.execute('[-5]')).toBe(5); // abs
    // expect(c.execute('50%')).toBe(0.5); // percent
    // expect(c.execute('(25 + 25)%')).toBe(0.5); // percent
    expect(c.execute('2*2*2*2')).toBe(16);
    expect(c.execute('-(2+5)')).toBe(-7); // negate expression
    expect(c.execute('[-2-5]')).toBe(7); // abs on expression
    expect(c.execute('(-1+50*2)')).toBe(99); // multiplication precedes addition
  });

  it('booleans', () => {
    expect(c.execute('true')).toBe(true);
    expect(c.execute('false')).toBe(false);
  });

  it('strings', () => {
    expect(c.execute('"blue"')).toBe('blue');
    expect(c.execute('\'blue\'')).toBe('blue');
    expect(c.execute('"#ff0000"')).toBe('#ff0000');
  });

  it('comparisons', () => {
    expect(c.execute('2 == 2')).toBe(true);
    expect(c.execute('2 == 3')).toBe(false);
    expect(c.execute('2 == 2 + 1')).toBe(false);
    expect(c.execute('2 equals 2')).toBe(true);
    expect(c.execute('2 equals 3')).toBe(false);
    expect(c.execute('2 equals 2 + 1')).toBe(false);

    expect(c.execute('2 != 2')).toBe(false);
    expect(c.execute('2 != 3')).toBe(true);
    expect(c.execute('2 <> 2')).toBe(false);
    expect(c.execute('2 <> 3')).toBe(true);
  });
});
