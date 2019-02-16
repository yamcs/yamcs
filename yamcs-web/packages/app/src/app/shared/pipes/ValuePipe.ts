import { Pipe, PipeTransform } from '@angular/core';
import { Value } from '@yamcs/client';
import { DateTimePipe } from './DateTimePipe';

const PREVIEW_LENGTH = 5;

@Pipe({ name: 'value' })
export class ValuePipe implements PipeTransform {

  constructor(private dateTimePipe: DateTimePipe) {}

  transform(value: Value): string | null {
    if (!value) {
      return null;
    }
    if (value.type === 'AGGREGATE') {
      let preview = '{';
      if (value.aggregateValue) {
        const n = Math.min(value.aggregateValue.name.length, PREVIEW_LENGTH);
        for (let i = 0; i < n; i++) {
          if (i !== 0) {
            preview += ', ';
          }
          preview += this.transformWithoutPreview(value.aggregateValue.value[i]);
        }
        if (n < value.aggregateValue.value.length) {
          preview += `, …`;
        }
      }
      return preview + '}';
    } else if (value.type === 'ARRAY') {
      let preview = '[';
      if (value.arrayValue) {
        const n = Math.min(value.arrayValue.length, PREVIEW_LENGTH);
        for (let i = 0; i < n; i++) {
          if (i !== 0) {
            preview += ', ';
          }
          preview += this.transformWithoutPreview(value.arrayValue[i]);
        }
        if (n < value.arrayValue.length) {
          preview += ', …]';
        }
        preview += ` (${value.arrayValue.length})`;
      } else {
        preview += ' (0)';
      }
      return preview;
    } else {
      return this.transformWithoutPreview(value);
    }
  }

  private transformWithoutPreview(value: Value): string {
    switch (value.type) {
      case 'AGGREGATE':
        return 'aggregate';
      case 'ARRAY':
        return 'array';
      case 'BOOLEAN':
        return '' + value.booleanValue;
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
        return this.dateTimePipe.transform(value.stringValue!)!;
      case 'UINT64':
        return '' + value.uint64Value;
      case 'SINT64':
        return '' + value.sint64Value;
      default:
        return 'Unsupported data type';
    }
  }
}
