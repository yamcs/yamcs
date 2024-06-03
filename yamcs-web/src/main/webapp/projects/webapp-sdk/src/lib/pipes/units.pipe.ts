import { Pipe, PipeTransform } from '@angular/core';
import { UnitInfo } from '../client';
import { getUnits } from '../utils';

@Pipe({
  standalone: true,
  name: 'units',
})
export class UnitsPipe implements PipeTransform {

  transform(unitSet?: UnitInfo[]): string | null {
    return getUnits(unitSet);
  }
}
