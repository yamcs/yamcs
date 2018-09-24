import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'printJson' })
export class PrintJsonPipe implements PipeTransform {

  transform(json: string): string | null {
    if (!json) {
      return json;
    }
    const props = [];
    const obj = JSON.parse(json) as {[key: string]: any};
    for (const key in obj) {
      if (obj.hasOwnProperty(key)) {
        props.push(`<b>${key}</b>: ${obj[key]}`);
      }
    }
    return props.join('<br>');
  }
}
