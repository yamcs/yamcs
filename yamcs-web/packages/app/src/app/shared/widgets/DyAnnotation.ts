/**
 * Annotation for a sample on a time-based plot.
 */
export type DyAnnotation = {
  series: string;
  x: number;
  shortText?: string;
  text?: string;
  icon?: string;
  width?: number;
  height?: number;
  cssClass?: string;
  tickHeight?: number;
  tickWidth?: number;
  tickColor?: string;
  attachAtBottom?: boolean;
};
