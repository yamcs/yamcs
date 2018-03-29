export type CustomBarsValue = [number, number, number] | null;

/**
 * Sample for a time-based plot.
 * http://dygraphs.com/data.html#array
 */
export type DySample = [Date, CustomBarsValue]
  | [Date, CustomBarsValue, CustomBarsValue]
  | [Date, CustomBarsValue, CustomBarsValue, CustomBarsValue]
  | [Date, CustomBarsValue, CustomBarsValue, CustomBarsValue, CustomBarsValue];
