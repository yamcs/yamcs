import { Pipe, PipeTransform } from '@angular/core';
import { convertDurationToMillis } from '../utils';

@Pipe({
  name: 'duration',
})
export class DurationPipe implements PipeTransform {
  transform(millis?: number | string | null): string | null {
    if (millis === null || millis === undefined) {
      return null;
    }

    const isStringifiedNumber =
      !isNaN(millis as any) && Number.isInteger(parseFloat(millis as any));

    let ms =
      typeof millis === 'string' && !isStringifiedNumber
        ? convertDurationToMillis(millis)
        : Number(millis);

    const isNegative = ms < 0;
    const absMs = Math.abs(ms);

    const totalSeconds = Math.floor(absMs / 1000);
    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    const milliseconds = Math.floor(absMs % 1000);

    let result = '';

    if (days) {
      result = hours ? `${days}d ${hours}h` : `${days}d`;
    } else if (hours) {
      result = minutes ? `${hours}h ${minutes}m` : `${hours}h`;
    } else if (minutes) {
      result = seconds ? `${minutes}m ${seconds}s` : `${minutes}m`;
    } else if (seconds) {
      result = `${seconds}s`;
    } else {
      result = `${milliseconds}ms`;
    }

    return isNegative ? `-${result}` : result;
  }
}
