import { Pipe, PipeTransform } from '@angular/core';
import { convertDurationToMillis } from '../utils';

@Pipe({
  standalone: true,
  name: 'duration',
})
export class DurationPipe implements PipeTransform {

  transform(millis?: number | string | null): string | null {
    if (millis === null || millis === undefined) {
      return null;
    }

    const isStringifiedNumber = !isNaN(millis as any)
      && Number.isInteger(parseFloat(millis as any));

    if (typeof millis === 'string' && !isStringifiedNumber) {
      millis = convertDurationToMillis(millis);
    }

    const totalSeconds = Math.floor((millis as any) / 1000);
    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds - (days * 86400)) / 3600);
    const minutes = Math.floor((totalSeconds - (days * 86400) - (hours * 3600)) / 60);
    const seconds = totalSeconds - (days * 86400) - (hours * 3600) - (minutes * 60);
    if (days) {
      if (hours) {
        return `${days}d ${hours}h`;
      } else {
        return `${days}d`;
      }
    } else if (hours) {
      if (minutes) {
        return `${hours}h ${minutes}m`;
      } else {
        return `${hours}h`;
      }
    } else if (minutes) {
      if (seconds) {
        return `${minutes}m ${seconds}s`;
      } else {
        return `${minutes}m`;
      }
    } else {
      return `${seconds}s`;
    }
  }
}
