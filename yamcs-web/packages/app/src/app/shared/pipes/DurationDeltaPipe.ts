import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'durationDelta' })
export class DurationDeltaPipe implements PipeTransform {

  transform(millis: number | null): string | null {
    if (millis == null) {
      return null;
    }
    const sign = (millis >= 0) ? '+' : '-';
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
}
