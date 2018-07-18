import { Injectable } from '@angular/core';
import { ConnectionInfo, Instance, InstanceClient, Processor, TimeInfo, YamcsClient } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';


/**
 * Singleton service for facilitating working with a websocket connection
 * to a specific instance.
 */
@Injectable({
  providedIn: 'root',
})
export class YamcsService {

  readonly yamcsClient = new YamcsClient();

  private selectedInstance: InstanceClient | null;

  readonly connectionInfo$ = new BehaviorSubject<ConnectionInfo | null>(null);
  private connectionInfoSubscription: Subscription;

  private timeInfo$ = new BehaviorSubject<TimeInfo | null>(null);
  private timeInfoSubscription: Subscription;

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
          this.selectedInstance.getConnectionInfoUpdates(),
          this.selectedInstance.getTimeUpdates(),
        ]).then(responses => {
          // Listen to server-controlled connection state (e.g. active instance, processor, clientId)
          const connectionInfoResponse = responses[0];
          this.connectionInfo$.next(connectionInfoResponse.connectionInfo);
          this.connectionInfoSubscription = connectionInfoResponse.connectionInfo$.subscribe(connectionInfo => {
            this.connectionInfo$.next(connectionInfo);
          });

          // Listen to time updates, so that we can easily provide actual mission time to components
          const timeResponse = responses[1];
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
    if (this.connectionInfoSubscription) {
      this.connectionInfoSubscription.unsubscribe();
    }
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
   * Returns the server-assigned client-id for the currently
   * active WebSocket connection.
   */
  getClientId() {
    return this.connectionInfo$.getValue()!.clientId;
  }

  /**
   * Returns the currently active processor (if any).
   */
  getProcessor(): Processor {
    return this.connectionInfo$.getValue()!.processor;
  }

  /**
   * Returns the InstanceClient for the currently active instance (if any).
   */
  getInstanceClient() {
    return this.selectedInstance;
  }

  /**
   * Returns latest mission time for the currently active instance (if any).
   */
  getMissionTime() {
    return new Date(Date.parse(this.timeInfo$.getValue()!.currentTimeUTC));
  }
}
