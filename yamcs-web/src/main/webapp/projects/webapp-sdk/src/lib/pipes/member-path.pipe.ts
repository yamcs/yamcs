import { Pipe, PipeTransform } from '@angular/core';
import { Parameter } from '../client';
import { getMemberPath } from '../utils';

/**
 * Prints a qualified name of a specific parameter member entry
 */
@Pipe({
  standalone: true,
  name: 'memberPath',
})
export class MemberPathPipe implements PipeTransform {

  transform(parameter: Parameter): string | null {
    return getMemberPath(parameter);
  }
}
