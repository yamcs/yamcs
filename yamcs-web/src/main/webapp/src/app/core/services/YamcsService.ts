import { APP_BASE_HREF } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ConnectionInfo, Processor, StorageClient, TimeSubscription, YamcsClient } from '../../client';

/**
 * Singleton service for facilitating working with a websocket connection
 */
@Injectable({
  providedIn: 'root',
})
export class YamcsService {

  readonly yamcsClient: YamcsClient;

  readonly connectionInfo$ = new BehaviorSubject<ConnectionInfo | null>(null);

  readonly time$ = new BehaviorSubject<string | null>(null);
  private timeSubscription: TimeSubscription;

  constructor(@Inject(APP_BASE_HREF) baseHref: string) {
    this.yamcsClient = new YamcsClient(baseHref);
    this.yamcsClient.prepareWebSocketClient();
  }

  setContext(instanceId: string, processorId?: string) {
    if (processorId) {
      return this.setProcessorContext(instanceId, processorId);
    } else {
      return this.setInstanceContext(instanceId);
    }
  }

  private setInstanceContext(instanceId: string) {
    return new Promise<void>((resolve, reject) => {
      const currentConnectionInfo = this.connectionInfo$.value;
      if (currentConnectionInfo) {
        if (currentConnectionInfo.instance === instanceId) {
          resolve();
          return;
        }
      }
      this.clearContext();
      this.yamcsClient.getInstance(instanceId).then(instance => {
        this.connectionInfo$.next({ instance: instance.name });

        // Listen to time updates, so that we can easily provide actual mission time to components
        this.timeSubscription = this.yamcsClient.createTimeSubscription({
          instance: instance.name,
        }, time => {
          this.time$.next(time.value);
          resolve();
        });
      }).catch(err => {
        reject(err);
      });
    });
  }

  private setProcessorContext(instanceId: string, processorId: string) {
    return new Promise<void>((resolve, reject) => {
      const currentConnectionInfo = this.connectionInfo$.value;
      if (currentConnectionInfo) {
        if (currentConnectionInfo.instance === instanceId && currentConnectionInfo.processor?.name === processorId) {
          resolve();
          return;
        }
      }
      this.clearContext();
      this.yamcsClient.getProcessor(instanceId, processorId).then(processor => {
        this.connectionInfo$.next({ processor, instance: processor.instance });

        // Listen to time updates, so that we can easily provide actual mission time to components
        this.timeSubscription = this.yamcsClient.createTimeSubscription({
          instance: instanceId,
          processor: processorId,
        }, time => {
          this.time$.next(time.value);
          resolve();
        });
      }).catch(err => {
        reject(err);
      });
    });
  }

  clearContext() {
    this.connectionInfo$.next(null);
    this.time$.next(null);
    if (this.timeSubscription) {
      this.timeSubscription.cancel();
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
