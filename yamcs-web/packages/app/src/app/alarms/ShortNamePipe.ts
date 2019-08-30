import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'shortName' })
export class ShortNamePipe implements PipeTransform {

  transform(name: string | null): string | null {
    if (!name) {
      return null;
    }
    const idx = name.lastIndexOf('/');
    return idx === -1 ? name : name.substring(idx + 1);
  }
}
