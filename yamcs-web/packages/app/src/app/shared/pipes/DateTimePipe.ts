import { Pipe, PipeTransform } from '@angular/core';
import * as utils from '../utils';

@Pipe({ name: 'datetime' })
export class DateTimePipe implements PipeTransform {

  transform(date: Date | string, addTimezone = true): string | null {
    if (!date) {
      return null;
    }
    return utils.printDateTime(date, addTimezone);
  }
}
