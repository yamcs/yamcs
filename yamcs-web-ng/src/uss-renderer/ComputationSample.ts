import { DataSourceSample } from './DataSourceSample';

export class ComputationSample implements DataSourceSample {

  rawValue = null;

  constructor(
    readonly generationTime: Date,
    readonly engValue: any,
    readonly acquisitionStatus: any,
    readonly monitoringResult: any,
  ) {}
}
