import { Pipe, PipeTransform } from '@angular/core';
import { Member, Parameter } from '../client';
import { getEntryForOffset } from '../utils';

@Pipe({
  standalone: true,
  name: 'entryForOffset',
})
export class EntryForOffsetPipe implements PipeTransform {

  transform(parameter: Parameter, offset: string): Parameter | Member | null {
    return getEntryForOffset(parameter, offset);
  }
}
