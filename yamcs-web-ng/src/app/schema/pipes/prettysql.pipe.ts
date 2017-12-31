import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'prettysql' })
export class PrettySqlPipe implements PipeTransform {

  transform(value: string): string {
    if (!value) {
      return value;
    }
    return value.replace(', ', ', <br>');
  }
}
