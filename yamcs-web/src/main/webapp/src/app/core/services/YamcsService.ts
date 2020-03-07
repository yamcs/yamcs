import { APP_BASE_HREF } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ConnectionInfo, Instance, InstanceClient, Processor, StorageClient, TimeSubscription, YamcsClient } from '../../client';

/**
 * Singleton service for facilitating working with a websocket connection
 */
@Injectable({
  providedIn: 'root',
})
export class YamcsService {

  readonly yamcsClient: YamcsClient;
  private selectedInstance: InstanceClient | null;

  readonly connectionInfo$ = new BehaviorSubject<ConnectionInfo | null>(null);

  readonly time$ = new BehaviorSubject<string | null>(null);
  private timeSubscription: TimeSubscription;

  constructor(@Inject(APP_BASE_HREF) baseHref: string) {
    this.yamcsClient = new YamcsClient(baseHref);
    this.yamcsClient.prepareWebSocketClient();
  }

  /**
   * Prepares a (new) instance.
   */
  selectInstance(instanceId: string) {
    return new Promise<Instance>((resolve, reject) => {
      const currentConnectionInfo = this.connectionInfo$.value;
      if (currentConnectionInfo) {
        if (currentConnectionInfo.instance.name === instanceId) {
          resolve(currentConnectionInfo.instance);
          return;
        }
      }
      this.unselectInstance();
      this.yamcsClient.getInstance(instanceId).then(instance => {
        this.selectedInstance = this.yamcsClient.createInstanceClient(instance.name);
        const processor = (instance.processors ? instance.processors[0] : undefined);

        this.connectionInfo$.next({ instance, processor });

        // Listen to time updates, so that we can easily provide actual mission time to components
        this.timeSubscription = this.yamcsClient.createTimeSubscription({
          instance: instance.name,
          processor: processor?.name,
        }, time => {
          this.time$.next(time.value);
          resolve(instance);
        });
      }).catch(err => {
        reject(err);
      });
    });
  }

  unselectInstance() {
    this.connectionInfo$.next(null);
    this.time$.next(null);
    if (this.timeSubscription) {
      this.timeSubscription.cancel();
    }
    if (this.selectedInstance) {
      this.selectedInstance.closeConnection();
      this.selectedInstance = null;
    }
  }

  /**
   * Returns the currently active instance (if any).
   */
  getInstance() {
    return this.connectionInfo$.getValue()!.instance;
  }

  /**
   * Returns the currently active processor (if any).
   */
  getProcessor(): Processor {
    return this.connectionInfo$.getValue()!.processor!;
  }

  /**
   * Returns the InstanceClient for the currently active instance (if any).
   */
  getInstanceClient() {
    return this.selectedInstance;
  }

  createStorageClient() {
    return new StorageClient(this.yamcsClient);
  }

  /**
   * Returns latest mission time for the currently active instance (if any).
   */
  getMissionTime() {
    return new Date(Date.parse(this.time$.getValue()!));
  }
}
