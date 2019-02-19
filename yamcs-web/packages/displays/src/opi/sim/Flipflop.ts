import { Sample } from '../../Sample';

/**
 * Generates an alternating boolean value
 */
export class Noise {

  private sample: Sample;

  constructor() {
    this.sample = new Sample(new Date(), true);
  }

  updateValue() {
    this.sample = new Sample(new Date(), !this.sample.value);
    return this.sample;
  }
}
