import { Pipe, PipeTransform } from '@angular/core';
import { Value } from '../client';
import { FormatValueOptions, Formatter } from '../services/formatter.service';

@Pipe({
  standalone: true,
  name: 'value',
})
export class ValuePipe implements PipeTransform {

  constructor(private formatter: Formatter) {
  }

  transform(value: Value | null | undefined, options?: FormatValueOptions): string | null {
    if (!value) {
      return null;
    }
    return this.formatter.formatValue(value, options);
  }
}
