import { Pipe, PipeTransform } from '@angular/core';
import { Formatter } from '../services/formatter.service';

@Pipe({
  standalone: true,
  name: 'hex',
})
export class HexPipe implements PipeTransform {

  constructor(private formatter: Formatter) {
  }

  transform(value: string | null): string | null {
    if (!value) {
      return null;
    }
    return this.formatter.formatHexPreview(value);
  }
}
