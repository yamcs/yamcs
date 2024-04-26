import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  standalone: true,
  name: 'millisDuration',
})
export class MillisDurationPipe implements PipeTransform {

  transform(millis?: number | null): string | null {
    if (millis === null || millis === undefined) {
      return null;
    }
    millis = Math.floor(millis);
    const totalSeconds = Math.floor(millis / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds - (hours * 3600)) / 60);
    const seconds = totalSeconds - (hours * 3600) - (minutes * 60);
    const remainingMillis = millis - (totalSeconds * 1000);
    if (hours) {
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
    } else if (seconds) {
      if (remainingMillis) {
        return `${seconds}s ${remainingMillis}ms`;
      } else {
        return `${seconds}s`;
      }
    } else {
      return `${remainingMillis}ms`;
    }
  }
}
