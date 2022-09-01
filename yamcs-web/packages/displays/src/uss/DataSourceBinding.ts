import { DataSourceSample } from './DataSourceSample';

export abstract class DataSourceBinding {

  dynamicProperty: string;
  label: string | null;
  usingRaw: boolean;
  sample?: DataSourceSample;
  valueType: string;

  // When plotting an enumerated parameter,
  // plot a numeric value (~ array index),
  // and show the enum state on the Y-axis instead.
  plotValues: string[] = [];

  constructor(readonly type: string) {
  }

  remapPlotValues() {
    const value = this.value;
    if (this.plotValues.indexOf(value) === -1) {
      this.plotValues.push(value);
      this.plotValues.sort(); // A-Z
    }
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

  get plotValue() {
    const value = this.value;
    return this.plotValues.indexOf(value);
  }
}
