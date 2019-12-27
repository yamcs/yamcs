import { Pipe, PipeTransform } from '@angular/core';
import { Value } from '../../client';
import * as utils from '../utils';

@Pipe({ name: 'value' })
export class ValuePipe implements PipeTransform {

  transform(value: Value): string | null {
    if (!value) {
      return null;
    }
    return utils.printValue(value);
  }
}
