import { Pipe, PipeTransform } from '@angular/core';
import * as utils from '../utils';

@Pipe({
  standalone: true,
  name: 'datetime',
})
export class DateTimePipe implements PipeTransform {

  transform(date: Date | string | null | undefined, addTimezone = true): string | null {
    if (!date) {
      return null;
    }
    return utils.printDateTime(date, addTimezone);
  }
}
