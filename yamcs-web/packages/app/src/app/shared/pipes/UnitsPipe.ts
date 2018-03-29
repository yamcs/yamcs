import { Pipe, PipeTransform } from '@angular/core';
import { UnitInfo } from '@yamcs/client';

@Pipe({ name: 'units' })
export class UnitsPipe implements PipeTransform {

  transform(unitSet: UnitInfo[]): string | null {
    if (!unitSet || unitSet.length === 0) {
      return null;
    }
    let res = '';
    for (const unitInfo of unitSet) {
      res += unitInfo.unit + ' ';
    }
    return res;
  }
}
