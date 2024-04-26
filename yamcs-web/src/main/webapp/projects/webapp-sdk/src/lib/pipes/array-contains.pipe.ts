import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  standalone: true,
  name: 'arrayContains',
})
export class ArrayContainsPipe implements PipeTransform {

  transform(haystack: any[] | null | undefined, needle: any): boolean {
    if (!haystack) {
      return false;
    }
    return haystack.indexOf(needle) !== -1;
  }
}
