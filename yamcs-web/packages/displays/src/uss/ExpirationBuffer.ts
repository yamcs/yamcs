import { Sample, SampleBuffer } from './SampleBuffer';

/**
 * Sample buffer that drops samples after a preset expiration time.
 * Remark that high-frequency data combined with a slow expiration
 * time may cause memory issues.
 */
export class ExpirationBuffer implements SampleBuffer {
  // Implementation Note: To avoid creating a timer, expirations
  // are managed in the push/snapshot methods directly.

  private buf: Sample[] = [];

  /**
   * Kept at same size as buf.
   * For each sample, contains the time when it was added to this buffer.
   */
  private receptionTimes: number[] = [];

  private previousExpirationCheck = new Date().getTime();

  /**
   * @param expirationTime number of milliseconds to keep samples in buffer
   */
  constructor(private expirationTime: number) {
  }

  push(sample: Sample) {
    const now = new Date().getTime();

    // Protect against lack of snapshot() calls
    if ((now - this.previousExpirationCheck) > 1000) {
      this.deleteExpiredSamples(now);
    }

    this.buf.push(sample);
    this.receptionTimes.push(now);
  }

  /**
   * Returns a copy of this buffer's current content with samples
   * sorted chronologically. This is a relatively expensive operation.
   */
  snapshot() {
    this.deleteExpiredSamples(new Date().getTime());
    return this.buf
      .sort((s1, s2) => s1[0].getTime() - s2[0].getTime());
  }

  /**
   * Deletes expired samples by scanning from the front until
   * the first non-expired sample is found.
   */
  private deleteExpiredSamples(checkTime: number) {
    let i = 0;
    while (i < this.buf.length) {
      if (checkTime - this.receptionTimes[i] < this.expirationTime) {
        break;
      }
      i++;
    }

    if (i > 0) {
      this.buf.splice(0, i);
      this.receptionTimes.splice(0, i);
    }
    this.previousExpirationCheck = checkTime;
  }
}
