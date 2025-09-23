export type CustomBarsValue = [number, number, number] | null;

/**
 * Sample for a time-based plot
 */
export type DySample =
  | [Date, CustomBarsValue]
  | [Date, CustomBarsValue, CustomBarsValue]
  | [Date, CustomBarsValue, CustomBarsValue, CustomBarsValue]
  | [Date, CustomBarsValue, CustomBarsValue, CustomBarsValue, CustomBarsValue];
