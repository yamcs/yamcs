import { Sample } from '../../Sample';

/**
 * Generates random values in the range [min, max]
 */
export class Noise {

  private sample: Sample;

  constructor(private avg: number, private stddev: number) {
    this.sample = new Sample(new Date(), avg);
  }

  updateValue() {
    this.sample = new Sample(new Date(), this.avg + this.nextGaussian() * this.stddev);
    return this.sample;
  }

  private nextGaussian() {
    let x1;
    let x2;
    let rad;
    do {
      x1 = 2 * Math.random() - 1;
      x2 = 2 * Math.random() - 1;
      rad = x1 * x1 + x2 * x2;
    } while (rad >= 1 || rad === 0);
    const c = Math.sqrt(-2 * Math.log(rad) / rad);
    return x1 * c;
  }
}
