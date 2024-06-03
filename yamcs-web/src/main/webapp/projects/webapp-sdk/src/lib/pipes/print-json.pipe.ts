import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  standalone: true,
  name: 'printJson',
})
export class PrintJsonPipe implements PipeTransform {

  transform(json: string): string | null {
    if (!json) {
      return json;
    }
    const obj = JSON.parse(json) as { [key: string]: any; };
    return this.doPrint(obj);
  }

  private doPrint(obj: { [key: string]: any; }, indent = ''): string {
    const props = [];
    for (const key in obj) {
      if (obj.hasOwnProperty(key)) {
        const val = obj[key];
        const label = `${indent}&bull;&nbsp;${key}:`;
        if (Array.isArray(val)) {
          props.push(label + '[');
          for (const item of val) {
            props.push(this.doPrint(val, indent + '&nbsp;&nbsp;'));
          }
          props.push(']');
        } else if (typeof val === 'object') {
          props.push(label);
          props.push(this.doPrint(val, indent + '&nbsp;&nbsp;'));
        } else {
          props.push(`${label} ${val}`);
        }
      }
    }
    return props.join('<br>');
  }
}
