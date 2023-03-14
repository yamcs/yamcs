import { Pipe, PipeTransform } from '@angular/core';

/**
 * Outputs the extension of a filename.
 */
@Pipe({ name: 'extension' })
export class ExtensionPipe implements PipeTransform {

  transform(filename: string | null): string | null {
    if (!filename) {
      return null;
    }

    let idx = filename.lastIndexOf('.');
    if (idx === -1) {
      return null;
    } else {
      return filename.substring(idx + 1);
    }
  }
}
