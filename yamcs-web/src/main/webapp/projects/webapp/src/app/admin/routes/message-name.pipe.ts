import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  standalone: true,
  name: 'messageName',
})
export class MessageNamePipe implements PipeTransform {

  transform(value: string): string | null {
    if (!value) {
      return value;
    }
    if (value === '.google.protobuf.Empty') {
      return null;
    }
    const idx = value.lastIndexOf('.');
    return idx > 0 ? (value.substr(idx + 1)) : value;
  }
}
