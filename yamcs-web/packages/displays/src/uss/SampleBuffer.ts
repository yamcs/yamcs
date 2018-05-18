/**
 * Sample for a time-based plot.
 * http://dygraphs.com/data.html#array
 */
export type Sample = [Date, number | null]
  | [Date, number | null, number | null]
  | [Date, number | null, number | null, number | null];

/**
 * Data structure that buffers realtime data.
 * Implementations should favor write efficiency over read efficiency.
 */
export interface SampleBuffer {

  /**
   * Adds a sample to this buffer. Samples may be added in non-chronological order.
   */
  push(sample: Sample): void;

  /**
   * Returns a time-sorted snapshot of this buffer's current content.
   */
  snapshot(): Sample[];
}
