import { Router } from '@angular/router';
import { Instance, NamedObjectId, StorageClient } from '@yamcs/client';
import { DisplayCommunicator } from '@yamcs/displays';
import { ConfigService } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';

/**
 * Resolves resources by fetching them via the Yamcs API.
 */
export class MyDisplayCommunicator implements DisplayCommunicator {

  private instance: Instance;
  private storageClient: StorageClient;

  constructor(private yamcs: YamcsService, private configService: ConfigService, private router: Router) {
    this.instance = yamcs.getInstance();
    this.storageClient = yamcs.createStorageClient();
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
    let instance = this.instance.name;
    if (this.configService.getDisplayScope() === 'GLOBAL') {
      instance = '_global';
    }
    return this.storageClient.getObjectURL(instance, bucketName, objectName);
  }

  async getObject(bucketName: string, objectName: string) {
    let instance = this.instance.name;
    if (this.configService.getDisplayScope() === 'GLOBAL') {
      instance = '_global';
    }
    return await this.storageClient.getObject(instance, bucketName, objectName);
  }

  async getXMLObject(bucketName: string, objectName: string) {
    const response = await this.getObject(bucketName, objectName);
    const text = await response.text();
    const xmlParser = new DOMParser();
    return xmlParser.parseFromString(text, 'text/xml') as XMLDocument;
  }
}
