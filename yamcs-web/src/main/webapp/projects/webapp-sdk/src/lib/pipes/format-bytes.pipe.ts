import { Pipe, PipeTransform } from '@angular/core';

const sizes = ['bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];

@Pipe({
  standalone: true,
  name: 'formatBytes',
})
export class FormatBytesPipe implements PipeTransform {

  transform(bytes: string | number | null, decimals = 2): string | null {
    const bytesNumber = Number(bytes);
    if (bytesNumber === 0) {
      return '0 bytes';
    } else if (!bytes) {
      return null;
    }
    const i = Math.floor(Math.log(bytesNumber) / Math.log(1024));
    return parseFloat((bytesNumber / Math.pow(1024, i)).toFixed(decimals)) + ' ' + sizes[i];
  }
}
