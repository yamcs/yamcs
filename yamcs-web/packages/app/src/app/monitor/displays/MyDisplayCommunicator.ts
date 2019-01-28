import { Router } from '@angular/router';
import { Instance, NamedObjectId, StorageClient } from '@yamcs/client';
import { DisplayCommunicator } from '@yamcs/displays';
import { YamcsService } from '../../core/services/YamcsService';

/**
 * Resolves resources by fetching them via the Yamcs API.
 */
export class MyDisplayCommunicator implements DisplayCommunicator {

  private instance: Instance;
  private storageClient: StorageClient;

  constructor(private yamcs: YamcsService, private router: Router) {
  }

  triggerParameterAction(id: NamedObjectId) {
    // Convert alias to qualified named before navigation
    this.yamcs.getInstanceClient()!.getParameterById(id).then(parameter => {
      this.router.navigate(['/mdb/parameters', parameter.qualifiedName], {
        queryParams: { instance: this.yamcs.getInstance().name }
      });
    });
  }

  getObjectURL(bucketName: string, objectName: string) {
    return this.storageClient.getObjectURL(this.instance.name, bucketName, objectName);
  }

  async getObject(bucketName: string, objectName: string) {
    return await this.storageClient.getObject(this.instance.name, bucketName, objectName);
  }

  async getXMLObject(bucketName: string, objectName: string) {
    const response = await this.getObject(bucketName, objectName);
    const text = await response.text();
    const xmlParser = new DOMParser();
    return xmlParser.parseFromString(text, 'text/xml') as XMLDocument;
  }
}
