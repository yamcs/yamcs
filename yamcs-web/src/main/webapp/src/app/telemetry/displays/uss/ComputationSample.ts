import { AlarmRange } from '../../../client';
import { DataSourceSample } from './DataSourceSample';

export class ComputationSample implements DataSourceSample {

  alarmRanges: AlarmRange[] = [];
  rangeCondition: any;

  constructor(
    readonly generationTime: Date,
    readonly engValue: any,
    readonly acquisitionStatus: any,
    readonly monitoringResult: any,
  ) { }
}
