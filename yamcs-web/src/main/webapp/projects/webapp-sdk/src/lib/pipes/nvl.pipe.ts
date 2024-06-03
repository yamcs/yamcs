import { Pipe, PipeTransform } from '@angular/core';

/**
 * Displays the second argument when the first argument
 * is null or undefined.
 *
 * This is sometimes more appropriate than relying on
 * falsiness. (e.g. it will also render 0, or detect unset booleans)
 */
@Pipe({
  standalone: true,
  name: 'nvl',
})
export class NvlPipe implements PipeTransform {

  transform(subject: any, replacement: any): any {
    if (subject === null || subject === undefined) {
      return replacement;
    }
    return subject;
  }
}
