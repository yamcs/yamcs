import { Pipe, PipeTransform } from '@angular/core';

const sizes = ['bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];

@Pipe({ name: 'formatBytes' })
export class FormatBytesPipe implements PipeTransform {

  transform(bytes: number | null, decimals = 2): string | null {
    if (bytes === 0) {
      return '0 Bytes';
    } else if (!bytes) {
      return null;
    }
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return parseFloat((bytes / Math.pow(1024, i)).toFixed(decimals)) + ' ' + sizes[i];
  }
}
