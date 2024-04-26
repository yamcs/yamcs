import { Pipe, PipeTransform } from '@angular/core';
import * as utils from '../utils';

@Pipe({
  standalone: true,
  name: 'nanosDuration',
})
export class NanosDurationPipe implements PipeTransform {

  transform(nanos: number | null): string | null {
    if (nanos == null) {
      return null;
    }
    nanos = Math.floor(nanos);
    const totalSeconds = Math.floor(nanos / 1000 / 1000 / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds - (hours * 3600)) / 60);
    const seconds = totalSeconds - (hours * 3600) - (minutes * 60);
    const subseconds = nanos - (totalSeconds * 1000 * 1000 * 1000);
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
    } else {
      const formatted = utils.lpad(subseconds, 9);
      return `${seconds}.${formatted} s`;
    }
  }
}
