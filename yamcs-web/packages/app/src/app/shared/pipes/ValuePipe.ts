import { Pipe, PipeTransform } from '@angular/core';
import { Value } from '@yamcs/client';
import { DateTimePipe } from './DateTimePipe';

@Pipe({ name: 'value' })
export class ValuePipe implements PipeTransform {

  constructor(private dateTimePipe: DateTimePipe) {}

  transform(value: Value): string | null {
    if (!value) {
      return null;
    }
    switch (value.type) {
      case 'FLOAT':
        return '' + value.floatValue;
      case 'DOUBLE':
        return '' + value.doubleValue;
      case 'UINT32':
        return '' + value.uint32Value;
      case 'SINT32':
        return '' + value.sint32Value;
      case 'BINARY':
        return '<binary>';
      case 'STRING':
        return value.stringValue!;
      case 'TIMESTAMP':
        return this.dateTimePipe.transform(value.stringValue!);
      case 'UINT64':
        return '' + value.uint64Value;
      case 'SINT64':
        return '' + value.sint64Value;
      case 'BOOLEAN':
        return '' + value.booleanValue;
      default:
        return 'Unsupported data type';
    }
  }
}
