import { Pipe, PipeTransform } from '@angular/core';
import * as utils from '../utils';

@Pipe({
  standalone: true,
  name: 'deltaWith',
})
export class DeltaWithPipe implements PipeTransform {

  transform(second: Date | string | undefined, first: Date | string, showSign = true): string | null {
    if (!first || !second) {
      return null;
    }

    const firstDate = utils.toDate(first);
    const secondDate = utils.toDate(second);
    let millis = secondDate.getTime() - firstDate.getTime();

    const sign = showSign ? (millis >= 0 ? '+' : '-') : '';
    millis = Math.abs(millis);

    const totalSeconds = Math.floor(millis / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds - (hours * 3600)) / 60);
    const seconds = totalSeconds - (hours * 3600) - (minutes * 60);
    const milliseconds = millis % 1000;

    const days = Math.floor(hours / 24);
    const years = Math.floor(days / 365);

    if (years) {
      return years === 1 ? `${sign}1 year` : `${sign}${years} years`;
    } else if (days) {
      return days === 1 ? `${sign}1 day` : `${sign}${days} days`;
    } else if (hours) {
      if (minutes) {
        return `${sign}${hours}h ${minutes}m`;
      } else {
        return `${sign}${hours}h`;
      }
    } else if (minutes) {
      if (seconds) {
        return `${sign}${minutes}m ${seconds}s`;
      } else {
        return `${sign}${minutes} min`;
      }
    } else if (seconds) {
      return `${sign}${seconds} s`;
    } else {
      return `${sign}${milliseconds} ms`;
    }
  }
}
