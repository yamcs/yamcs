import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  standalone: true,
  name: 'displayType',
})
export class DisplayTypePipe implements PipeTransform {

  transform(path?: string): string | null {
    if (!path) {
      return null;
    }
    const lc = path.toLowerCase();
    if (lc.endsWith('.opi')) {
      return 'Operator Interface';
    } else if (lc.endsWith('.par')) {
      return 'Parameter Table';
    } else if (lc.endsWith('.js')) {
      return 'Script File';
    } else if (lc.indexOf('.') !== -1) {
      const extension = lc.substr(lc.lastIndexOf('.') + 1);
      return extension.toUpperCase() + ' File';
    } else {
      return null;
    }
  }
}
