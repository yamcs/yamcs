import { DataSourceSample } from './DataSourceSample';
import { AlarmRange } from '../yamcs-client';

export class ComputationSample implements DataSourceSample {

  alarmRanges: AlarmRange[] = [];

  constructor(
    readonly generationTime: Date,
    readonly engValue: any,
    readonly acquisitionStatus: any,
    readonly monitoringResult: any,
  ) {}
}
