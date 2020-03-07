import { APP_BASE_HREF } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { ConnectionInfo, Instance, InstanceClient, Processor, StorageClient, TimeInfo, YamcsClient } from '../../client';

/**
 * Singleton service for facilitating working with a websocket connection
 * to a specific instance.
 */
@Injectable({
  providedIn: 'root',
})
export class YamcsService {

  readonly yamcsClient: YamcsClient;
  private selectedInstance: InstanceClient | null;

  readonly connectionInfo$ = new BehaviorSubject<ConnectionInfo | null>(null);

  private timeInfo$ = new BehaviorSubject<TimeInfo | null>(null);
  private timeInfoSubscription: Subscription;

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

        Promise.all([
          this.selectedInstance.getTimeUpdates(),
        ]).then(responses => {
          this.connectionInfo$.next({
            instance: instance,
            processor: (instance.processors ? instance.processors[0] : undefined),
          });

          // Listen to time updates, so that we can easily provide actual mission time to components
          const timeResponse = responses[0];
          this.timeInfo$.next(timeResponse.timeInfo);
          this.timeInfoSubscription = timeResponse.timeInfo$.subscribe(timeInfo => {
            this.timeInfo$.next(timeInfo);
          });
          resolve(instance);
        }).catch(err => {
          reject(err);
        });
      }).catch(err => {
        reject(err);
      });
    });
  }

  unselectInstance() {
    this.connectionInfo$.next(null);
    this.timeInfo$.next(null);
    if (this.timeInfoSubscription) {
      this.timeInfoSubscription.unsubscribe();
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
    return new Date(Date.parse(this.timeInfo$.getValue()!.currentTime));
  }
}
