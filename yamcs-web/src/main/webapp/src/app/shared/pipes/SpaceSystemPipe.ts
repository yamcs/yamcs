import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'spaceSystemName' })
export class SpaceSystemPipe implements PipeTransform {

  transform(name: string | null): string | null {
    if (!name) {
      return null;
    }
    const idx = name.lastIndexOf('/');
    return idx === -1 ? '' : name.substring(0, idx);
  }
}
