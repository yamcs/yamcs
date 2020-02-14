import { Pipe, PipeTransform } from '@angular/core';
import * as utils from '../utils';

@Pipe({ name: 'hex' })
export class HexPipe implements PipeTransform {

  transform(value: string | null): string | null {
    if (!value) {
      return null;
    }
    const hex = utils.convertBase64ToHex(value);
    if (value.length > 8) {
      return '0x' + hex.slice(0, 8) + '...';
    } else {
      return '0x' + hex;
    }
  }
}
