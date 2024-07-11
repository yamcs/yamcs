import { Pipe, PipeTransform } from '@angular/core';
import { Formatter } from '../services/formatter.service';

@Pipe({
  standalone: true,
  name: 'datetime',
})
export class DateTimePipe implements PipeTransform {

  constructor(private formatter: Formatter) {
  }

  transform(date: Date | string | null | undefined, addTimezone = true): string | null {
    if (!date) {
      return null;
    }
    return this.formatter.formatDateTime(date, addTimezone);
  }
}
