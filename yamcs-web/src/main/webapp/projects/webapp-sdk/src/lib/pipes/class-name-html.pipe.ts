import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  standalone: true,
  name: 'classNameHtml',
})
export class ClassNameHtmlPipe implements PipeTransform {

  transform(qualifiedName: string): string | null {
    if (!qualifiedName) {
      return null;
    }
    let idx = qualifiedName.lastIndexOf('.');

    if (idx === -1) {
      return qualifiedName;
    } else {
      const classPackage = qualifiedName.substring(0, idx + 1);
      const className = qualifiedName.substring(idx + 1);
      return `<small>${classPackage}</small>${className}`;
    }
  }
}
