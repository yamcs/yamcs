import { Pipe, PipeTransform } from '@angular/core';
import * as utils from '../utils';

@Pipe({
  standalone: true,
  name: 'hex',
})
export class HexPipe implements PipeTransform {

  transform(value: string | null): string | null {
    if (!value) {
      return null;
    }
    return utils.printHexPreview(value);
  }
}
