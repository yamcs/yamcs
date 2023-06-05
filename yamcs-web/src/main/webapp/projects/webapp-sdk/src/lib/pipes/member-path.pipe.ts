import { Pipe, PipeTransform } from '@angular/core';
import { Parameter } from '../client';

/**
 * Prints a qualified name of a specific parameter member entry
 */
@Pipe({ name: 'memberPath' })
export class MemberPathPipe implements PipeTransform {

  transform(parameter: Parameter): string | null {
    if (!parameter) {
      return null;
    }
    let result = parameter.qualifiedName;
    if (parameter.path) {
      for (let i = 0; i < parameter.path.length; i++) {
        const el = parameter.path[i];
        if (el.startsWith('[')) {
          result += el;
        } else {
          result += '.' + el;
        }
      }
    }
    return result;
  }
}
