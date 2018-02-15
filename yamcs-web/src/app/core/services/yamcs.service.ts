import { Injectable } from '@angular/core';
import { YamcsClient, InstanceClient } from '../../../yamcs-client';
import { HttpClient } from '@angular/common/http';


/**
 * Singleton service for facilitating working with a websocket connection
 * to a specific instance.
 */
@Injectable()
export class YamcsService {

  readonly yamcsClient: YamcsClient;
  private selectedInstance: InstanceClient;

  constructor(http: HttpClient) {
    this.yamcsClient = new YamcsClient(http);
  }

  switchInstance(instance: string) {
    if (this.selectedInstance) {
      if (this.selectedInstance.instance === instance) {
        return this.selectedInstance;
      } else {
        this.selectedInstance.closeConnection();
      }
    }

    this.selectedInstance = this.yamcsClient.selectInstance(instance);
    return this.selectedInstance;
  }

  getSelectedInstance() {
    return this.selectedInstance;
  }
}
