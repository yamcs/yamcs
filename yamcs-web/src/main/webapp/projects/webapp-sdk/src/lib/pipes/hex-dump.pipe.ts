import { Pipe, PipeTransform } from '@angular/core';
import * as utils from '../utils';

@Pipe({
  standalone: true,
  name: 'hexDump',
})
export class HexDumpPipe implements PipeTransform {

  transform(value: string | null): string | null {
    if (!value) {
      return null;
    }
    return utils.printHexDump(value);
  }
}
