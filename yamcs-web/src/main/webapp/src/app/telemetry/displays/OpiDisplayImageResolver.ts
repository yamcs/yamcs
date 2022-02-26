import { DefaultImageResolver, Display } from '@yamcs/opi';
import { StorageClient } from '../../client';

export class OpiDisplayImageResolver extends DefaultImageResolver {

  constructor(private storageClient: StorageClient, display: Display) {
    super(display);
  }

  resolve(file: string) {
    if (file.startsWith('ys://')) {
      const matchResult = file.match(/ys:\/\/([^\\\/]+)\/(.+)/);
      if (matchResult) {
        return this.storageClient.getObjectURL('_global', matchResult[1], matchResult[2]);
      }
    }
    return super.resolve(file);
  }
}
