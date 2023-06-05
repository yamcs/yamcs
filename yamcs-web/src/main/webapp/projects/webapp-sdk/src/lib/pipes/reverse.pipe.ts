import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'reverse' })
export class ReversePipe implements PipeTransform {

  transform(value: any[]): any[] | null {
    if (!value) {
      return value;
    }
    return value.slice().reverse();
  }
}
