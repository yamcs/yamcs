import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'binarySize' })
export class BinarySizePipe implements PipeTransform {

  transform(base64: string | null): number | null {
    if (!base64) {
      return null;
    }
    const raw = atob(base64);
    return raw.length;
  }
}
