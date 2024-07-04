import { Injectable } from '@angular/core';
import { FormatOptionsWithTZ, formatInTimeZone } from 'date-fns-tz';
import { Value } from '../client';
import * as utils from '../utils';
import { ConfigService } from './config.service';

const FNS_OPTS: FormatOptionsWithTZ = { weekStartsOn: 1 };
const PREVIEW_LENGTH = 3;

export interface FormatValueOptions {
  maxBytes?: number;
}

@Injectable({ providedIn: 'root' })
export class Formatter {

  private timezone: string;
  private DT_FMT_LONG = 'yyyy-MM-dd HH:mm:ss.SSS';
  private DT_FMT_LONG_TZ = 'yyyy-MM-dd HH:mm:ss.SSS zzz';

  constructor(configService: ConfigService) {
    this.timezone = configService.getConfig().utc
      ? 'UTC'
      : Intl.DateTimeFormat().resolvedOptions().timeZone;
  }

  isUTC() {
    return this.timezone === 'UTC';
  }

  getTimezone() {
    return this.timezone;
  }

  formatDateTime(date: Date | string | number, addTimezone = true): string {
    if (typeof date === 'string' && !date.endsWith('Z')) {
      date += 'Z';
    }

    if (addTimezone) {
      // Note: we do not specify a locale, so that the system locale
      // is used.
      //
      // For example: en-US would not display CEST but GMT+2.
      return formatInTimeZone(date, this.timezone, this.DT_FMT_LONG_TZ, FNS_OPTS);
    } else {
      return formatInTimeZone(date, this.timezone, this.DT_FMT_LONG, FNS_OPTS);
    }
  }

  formatValue(value: Value, options?: FormatValueOptions) {
    if (value.type === 'AGGREGATE') {
      let preview = '{';
      if (value.aggregateValue) {
        const n = Math.min(value.aggregateValue.name.length, PREVIEW_LENGTH);
        for (let i = 0; i < n; i++) {
          if (i !== 0) {
            preview += ', ';
          }
          preview += value.aggregateValue.name[i] + ': ' + this.formatValueWithoutPreview(value.aggregateValue.value[i], options);
        }
        if (n < value.aggregateValue.value.length) {
          preview += `, …`;
        }
      }
      return preview + '}';
    } else if (value.type === 'ARRAY') {
      let preview = '';
      if (value.arrayValue) {
        preview += `(${value.arrayValue.length}) [`;
        const n = Math.min(value.arrayValue.length, PREVIEW_LENGTH);
        for (let i = 0; i < n; i++) {
          if (i !== 0) {
            preview += ', ';
          }
          preview += this.formatValueWithoutPreview(value.arrayValue[i], options);
        }
        if (n < value.arrayValue.length) {
          preview += ', …';
        }
        preview += ']';
      } else {
        preview += '(0) []';
      }
      return preview;
    } else {
      return this.formatValueWithoutPreview(value, options);
    }
  }

  private formatValueWithoutPreview(value: Value, options?: FormatValueOptions): string {
    switch (value.type) {
      case 'AGGREGATE':
        return '{…}';
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
        if (options?.maxBytes !== undefined) {
          return this.formatHexPreview('' + value.binaryValue, options.maxBytes);
        } else {
          return this.formatHexPreview('' + value.binaryValue);
        }
      case 'ENUMERATED':
      case 'STRING':
        return value.stringValue!;
      case 'TIMESTAMP':
        return this.formatDateTime(value.stringValue!);
      case 'UINT64':
        return '' + value.uint64Value;
      case 'SINT64':
        return '' + value.sint64Value;
      case 'NONE':
        return '';
      default:
        return 'Unsupported data type';
    }
  }

  formatHexPreview(binaryValue: string, maxBytes = 16) {
    const hex = utils.convertBase64ToHex(binaryValue);
    if (hex.length > maxBytes * 2) {
      return '0x' + hex.slice(0, maxBytes * 2) + '…';
    } else if (hex.length > 0) {
      return '0x' + hex;
    } else {
      return '';
    }
  }

  formatHexDump(base64: string) {
    function lpad(hex: string, width: number) {
      if (hex.length >= width) {
        return hex;
      } else {
        return new Array(width - hex.length + 1).join('0') + hex;
      }
    }

    const raw = window.atob(base64);
    let result = '';
    let charCount = 0;
    let lineAscii = '';
    for (let i = 0; i < raw.length; i++) {
      if (i % 16 === 0) {
        const charCountHex = charCount.toString(16);
        result += lpad(charCountHex, 8);
        result += ': ';
      }
      const code = raw.charCodeAt(i);
      const hex = code.toString(16);
      if (32 <= code && code <= 126) {
        lineAscii += raw[i];
      } else {
        lineAscii += '.';
      }

      result += (hex.length === 2 ? hex : '0' + hex);
      if ((i + 1) % 2 === 0) {
        result += ' ';
      }

      if ((i + 1) % 16 === 0) {
        result += ' ' + lineAscii + '\n';
        lineAscii = '';
        charCount += 16;
      }
    }
    return result;
  }
}
