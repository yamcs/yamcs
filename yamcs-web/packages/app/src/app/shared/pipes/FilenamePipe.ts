import { Pipe, PipeTransform } from '@angular/core';

/**
 * Outputs the filename of a path string. The path may end with a trailing slash which is preserved.
 */
@Pipe({ name: 'filename' })
export class FilenamePipe implements PipeTransform {

  transform(path: string): string | null {
    if (!path) {
      return null;
    }
    let idx = path.lastIndexOf('/');
    if (path.endsWith('/')) {
      idx = path.substring(0, path.length - 1).lastIndexOf('/');
    }

    if (idx === -1) {
      return path;
    } else {
      return path.substring(idx + 1);
    }
  }
}
