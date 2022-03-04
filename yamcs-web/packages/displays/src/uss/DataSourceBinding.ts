import { DataSourceSample } from './DataSourceSample';

export abstract class DataSourceBinding {

  dynamicProperty: string;
  label: string | null;
  usingRaw: boolean;
  sample?: DataSourceSample;

  constructor(readonly type: string) {
  }

  get value() {
    if (this.sample) {
      if (this.usingRaw) {
        return this.sample.rawValue;
      } else {
        return this.sample.engValue;
      }
    }
  }
}
