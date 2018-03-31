import { Injectable } from '@angular/core';
import { YamcsClient, InstanceClient, Instance } from '@yamcs/client';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';


/**
 * Singleton service for facilitating working with a websocket connection
 * to a specific instance.
 */
@Injectable()
export class YamcsService {

  readonly yamcsClient: YamcsClient;
  readonly instance$ = new BehaviorSubject<Instance | null>(null);

  private selectedInstance: InstanceClient;

  constructor(http: HttpClient) {
    this.yamcsClient = new YamcsClient();
  }

  switchInstance(instance: Instance) {
    if (this.selectedInstance) {
      if (this.selectedInstance.instance === instance.name) {
        return this.selectedInstance;
      } else {
        this.selectedInstance.closeConnection();
      }
    }

    this.instance$.next(instance);
    this.selectedInstance = this.yamcsClient.selectInstance(instance.name);
    return this.selectedInstance;
  }

  /**
   * Returns the currently active instance (if any).
   */
  getInstance() {
    return this.instance$.getValue()!;
  }

  /**
   * Returns the InstanceClient for the currently active instance (if any).
   */
  getInstanceClient() {
    return this.selectedInstance;
  }
}
