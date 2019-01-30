import { Sample } from '../../Sample';

/**
 * Generates random values in the range [min, max]
 */
export class Noise {

  private sample: Sample;

  constructor(private min: number, private max: number) {
    this.sample = new Sample(new Date(), min);
  }

  updateValue() {
    this.sample = new Sample(new Date(), Math.random() * (this.max - this.min) + this.min);
    return this.sample;
  }
}
