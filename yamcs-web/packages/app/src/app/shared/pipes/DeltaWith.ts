import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'deltaWith' })
export class DeltaWithPipe implements PipeTransform {

  transform(second: Date | string, first: Date | string): string | null {
    if (!first || !second) {
      return null;
    }

    const firstDate = this.toDate(first);
    const secondDate = this.toDate(second);
    let millis = secondDate.getTime() - firstDate.getTime();

    const sign = (millis >= 0) ? '+' : '-';
    millis = Math.abs(millis);

    const totalSeconds = Math.floor(millis / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds - (hours * 3600)) / 60);
    const seconds = totalSeconds - (hours * 3600) - (minutes * 60);
    const milliseconds = millis % 1000;
    if (hours) {
      if (minutes) {
        return `${sign}${hours}h ${minutes}m`;
      } else {
        return `${sign}${hours}h`;
      }
    } else if (minutes) {
      if (seconds) {
        return `${sign}${minutes}m ${seconds}s`;
      } else {
        return `${sign}${minutes}m`;
      }
    } else {
      const prefixed = '00' + milliseconds;
      return `${sign}${seconds}.${prefixed.substr(prefixed.length - 3)}`;
    }
  }

  private toDate(obj: any): Date {
    if (!obj) {
      return obj;
    }

    if (obj instanceof Date) {
      return obj;
    } else if (typeof obj === 'number') {
      return new Date(obj);
    } else if (typeof obj === 'string') {
      return new Date(Date.parse(obj));
    } else {
      throw new Error(`Cannot convert '${obj}' to Date`);
    }
  }
}
