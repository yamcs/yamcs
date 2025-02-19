import { Pipe, PipeTransform } from '@angular/core';
import { getExtension } from '../utils';

/**
 * Outputs the extension of a filename.
 */
@Pipe({
  name: 'extension',
})
export class ExtensionPipe implements PipeTransform {

  transform(filename: string | null): string | null {
    return getExtension(filename);
  }
}
