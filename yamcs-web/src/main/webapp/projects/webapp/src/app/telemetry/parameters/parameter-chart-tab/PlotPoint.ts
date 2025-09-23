export interface PlotPoint {
  time: number; // Start of sample interval
  firstTime: number; // Lowest actual time within sample interval
  lastTime: number; // Highest actual time within sample interval
  n: number;
  avg: number | null;
  min: number | null;
  max: number | null;
}
