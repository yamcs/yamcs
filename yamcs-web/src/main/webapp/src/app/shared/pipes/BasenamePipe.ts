import { Pipe, PipeTransform } from '@angular/core';

/**
 * Outputs the basename of a path string (no extension).
 */
@Pipe({ name: 'basename' })
export class BasenamePipe implements PipeTransform {

  transform(path: string): string | null {
    if (!path) {
      return null;
    }

    const idx = path.lastIndexOf('.');
    if (idx === -1) {
      return path;
    } else {
      return path.substring(0, idx);
    }
  }
}
