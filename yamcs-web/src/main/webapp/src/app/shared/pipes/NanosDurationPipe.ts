import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'nanosDuration' })
export class NanosDurationPipe implements PipeTransform {

  transform(totalNanos: number | null): string | null {
    if (totalNanos == null) {
      return null;
    }
    totalNanos = Math.floor(totalNanos);
    const totalMillis = Math.floor(totalNanos / 1000);
    const totalSeconds = Math.floor(totalMillis / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds - (hours * 3600)) / 60);
    const seconds = totalSeconds - (hours * 3600) - (minutes * 60);
    const millis = totalMillis - (totalSeconds * 1000);
    const nanos = totalNanos - (totalMillis * 1000);
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
      if (millis) {
        return `${seconds}s ${millis}ms`;
      } else {
        return `${seconds}s`;
      }
    } else if (millis) {
      if (nanos) {
        return `${millis}ms ${nanos}ns`;
      } else {
        return `${nanos}ns`;
      }
    } else {
      return `${nanos}ns`;
    }
  }
}
