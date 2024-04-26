import { Pipe, PipeTransform } from '@angular/core';
import { getFilename } from '../utils';

/**
 * Outputs the filename of a path string. The path may end with a trailing slash which is preserved.
 */
@Pipe({
  standalone: true,
  name: 'filename',
})
export class FilenamePipe implements PipeTransform {

  transform(path: string): string | null {
    return getFilename(path);
  }
}
