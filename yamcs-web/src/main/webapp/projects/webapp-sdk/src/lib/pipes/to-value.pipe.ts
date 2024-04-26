import { Pipe, PipeTransform } from '@angular/core';
import { Value } from '../client';
import * as utils from '../utils';

@Pipe({
  standalone: true,
  name: 'tovalue',
})
export class ToValuePipe implements PipeTransform {

  transform(value: any | null | undefined): Value | null {
    if (value === null || value === undefined) {
      return null;
    }
    return utils.toValue(value);
  }
}
