import { Pipe, PipeTransform } from '@angular/core';
import { Value } from '../client';
import * as utils from '../utils';

@Pipe({
  standalone: true,
  name: 'value',
})
export class ValuePipe implements PipeTransform {

  transform(value: Value | null | undefined, options?: utils.PrintValueOptions): string | null {
    if (!value) {
      return null;
    }
    return utils.printValue(value, options);
  }
}
