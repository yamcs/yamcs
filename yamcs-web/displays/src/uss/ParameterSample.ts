import { DataSourceSample } from './DataSourceSample';
import { AlarmRange, ParameterValue } from '@yamcs/client';

export class ParameterSample implements DataSourceSample {

  opsName: string;

  generationTime: Date;
  expirationTime: Date;

  rawValue: any;
  engValue: any;

  acquisitionStatus: string;
  monitoringResult: string;
  alarmRanges: AlarmRange[];
  rangeCondition: any;

  constructor(pval: ParameterValue) {
    this.opsName = pval.id.name;
    this.generationTime = new Date(Date.parse(pval.generationTimeUTC));
    this.expirationTime = new Date(Date.parse(pval.expirationTimeUTC));
    this.acquisitionStatus = pval.acquisitionStatus;
    this.monitoringResult = pval.monitoringResult;
    if (pval.rawValue) {
      this.rawValue = this.getValue(pval.rawValue);
    }
    this.engValue = this.getValue(pval.engValue);
    this.alarmRanges = pval.alarmRange || [];
    this.rangeCondition = pval.rangeCondition;
  }

  private getValue(value: any) {
    switch (value.type) {
      case 'FLOAT':
        return value.floatValue;
      case 'DOUBLE':
        return value.doubleValue;
      case 'UINT32':
        return value.uint32Value;
      case 'SINT32':
        return value.sint32Value;
      case 'UINT64':
        return value.uint64Value;
      case 'SINT64':
        return value.sint64Value;
      case 'BOOLEAN':
        return value.booleanValue;
      case 'TIMESTAMP':
        return value.timestampValue;
      case 'BINARY':
        return window.atob(value.binaryValue);
      case 'STRING':
        return value.stringValue;
    }
  }
}
