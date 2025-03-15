import { Pipe, PipeTransform } from '@angular/core';
import { getBasename } from '../utils';

/**
 * Outputs the basename of a path string (no extension).
 */
@Pipe({
  name: 'basename',
})
export class BasenamePipe implements PipeTransform {
  transform(path: string | null): string | null {
    return getBasename(path);
  }
}
