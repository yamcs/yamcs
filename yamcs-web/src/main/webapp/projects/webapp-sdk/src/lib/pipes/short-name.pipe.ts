import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  standalone: true,
  name: 'shortName',
})
export class ShortNamePipe implements PipeTransform {

  transform(name: string | null | undefined): string | null {
    if (!name) {
      return null;
    }
    const idx = name.lastIndexOf('/');
    return idx === -1 ? name : name.substring(idx + 1);
  }
}
