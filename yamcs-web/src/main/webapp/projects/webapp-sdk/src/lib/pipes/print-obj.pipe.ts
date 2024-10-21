import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  standalone: true,
  name: 'printObj',
})
export class PrintObjPipe implements PipeTransform {

  transform(obj: any): string | null {
    if (!obj) {
      return obj;
    }
    return this.doPrint(obj);
  }

  private doPrint(obj: { [key: string]: any; }): string {
    if (typeof obj === 'number') {
      return '' + obj;
    } else if (typeof obj === 'boolean') {
      return '' + obj;
    } else if (typeof obj === 'string') {
      return obj;
    } else {
      const props = [];
      for (const key in obj) {
        if (obj.hasOwnProperty(key)) {
          const val = obj[key];
          const label = `${key}:`;
          if (Array.isArray(val)) {
            props.push(label + '[');
            for (const item of val) {
              props.push(this.doPrint(val));
            }
            props.push(']');
          } else if (typeof val === 'object') {
            props.push(label);
            props.push(this.doPrint(val));
          } else {
            props.push(`${label} ${val}`);
          }
        }
      }
      return props.join('');
    }
  }
}
