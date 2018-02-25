import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'datetime' })
export class DateTimePipe implements PipeTransform {

  transform(date: Date): string | null {
    if (!date) {
      return null;
    }
    const dateString = date.toISOString();
    return dateString.replace('T', ' ').replace('Z', ' UTC');
  }
}
