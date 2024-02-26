import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'storageUrl' })
export class StorageUrlPipe implements PipeTransform {

  transform(objectName: string, bucket: string): string | null {
    if (!bucket || !objectName) {
      return null;
    }
    return `ys://${bucket}/${objectName}`;
  }
}
