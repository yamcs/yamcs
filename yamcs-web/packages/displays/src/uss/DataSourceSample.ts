import { AlarmRange } from '@yamcs/client';

export interface DataSourceSample {

  generationTime: Date;
  rawValue?: any;
  engValue: any;
  acquisitionStatus: string;
  monitoringResult: string;
  alarmRanges: AlarmRange[];
  rangeCondition: any;
}
