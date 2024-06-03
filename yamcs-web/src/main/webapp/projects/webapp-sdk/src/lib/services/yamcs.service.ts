import { APP_BASE_HREF } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Clearance, ClearanceSubscription, ConnectionInfo, Processor, SessionListener, StorageClient, TimeSubscription, YamcsClient } from '../client';
import { FrameLossListener } from '../client/FrameLossListener';
import { getDefaultProcessor } from '../utils';
import { ConfigService } from './config.service';
import { MessageService } from './message.service';

/**
 * Singleton service for facilitating working with a websocket connection
 */
@Injectable({
  providedIn: 'root',
})
export class YamcsService implements FrameLossListener, SessionListener {

  readonly yamcsClient: YamcsClient;

  readonly connectionInfo$ = new BehaviorSubject<ConnectionInfo | null>(null);

  readonly clearance$ = new BehaviorSubject<Clearance | null>(null);
  private clearanceSubscription: ClearanceSubscription;

  readonly time$ = new BehaviorSubject<string | null>(null);
  private timeSubscription: TimeSubscription;

  readonly range$ = new BehaviorSubject<string>('PT15M');

  readonly sessionEnded$ = new BehaviorSubject<boolean>(false);

  constructor(
    @Inject(APP_BASE_HREF) baseHref: string,
    private router: Router,
    private messageService: MessageService,
    private configService: ConfigService,
  ) {
    this.yamcsClient = new YamcsClient(baseHref, this, this);
  }

  onFrameLoss() {
    this.messageService.showWarning('A gap was detected in one of the data feeds. Typically this occurs when data is fastly updating.');
  }

  onSessionEnd(message: string): void {
    this.sessionEnded$.next(true);
  }

  setContext(instanceId: string, processorId?: string) {
    if (processorId) {
      return this.setProcessorContext(instanceId, processorId);
    } else {
      return this.setInstanceContext(instanceId);
    }
  }

  async switchContext(instance: string, processor?: string) {
    let newContext = instance;
    if (processor) {
      newContext += '__' + processor;
    } else {
      const instanceDetail = await this.yamcsClient.getInstance(instance);
      const defaultProcessor = getDefaultProcessor(instanceDetail);
      if (defaultProcessor) {
        newContext += '__' + defaultProcessor;
      }
    }

    this.router.navigate(['/context-switch', newContext, this.router.url], {
      skipLocationChange: true,
    });
  }

  private setInstanceContext(instanceId: string) {
    return new Promise<void>((resolve, reject) => {
      const currentConnectionInfo = this.connectionInfo$.value;
      if (currentConnectionInfo) {
        if (currentConnectionInfo.instance?.name === instanceId) {
          resolve();
          return;
        }
      }
      this.clearContext();
      this.yamcsClient.getInstance(instanceId).then(instance => {
        this.connectionInfo$.next({ instance });

        // Don't wait on WebSocket. Lots of pages require mission time
        this.time$.next(instance.missionTime);

        // Listen to time updates, so that we can easily provide actual mission time to components
        this.timeSubscription = this.yamcsClient.createTimeSubscription({
          instance: instance.name,
        }, time => {
          this.time$.next(time.value);
          resolve();
        });
        this.clearanceSubscription = this.yamcsClient.createClearanceSubscription(clearance => {
          this.clearance$.next(clearance);
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
        if (currentConnectionInfo.instance?.name === instanceId && currentConnectionInfo.processor?.name === processorId) {
          resolve();
          return;
        }
      }
      this.clearContext();
      Promise.all([
        this.yamcsClient.getInstance(instanceId),
        this.yamcsClient.getProcessor(instanceId, processorId),
        this.yamcsClient.getInstanceConfig(instanceId),
      ]).then(result => {
        this.connectionInfo$.next({ instance: result[0], processor: result[1] });

        // Don't wait on WebSocket. Lots of pages require mission time
        this.time$.next(result[1].time);

        this.configService.setInstanceConfig(result[2]);

        // Listen to time updates, so that we can easily provide actual mission time to components
        this.timeSubscription = this.yamcsClient.createTimeSubscription({
          instance: instanceId,
          processor: processorId,
        }, time => {
          this.time$.next(time.value);
          resolve();
        });
        this.clearanceSubscription = this.yamcsClient.createClearanceSubscription(clearance => {
          this.clearance$.next(clearance);
          resolve();
        });
      }).catch(err => {
        reject(err);
      });
    });
  }

  /**
  * Returns the currently active context (if any).
  * This is the combination of an instance with a processor.
  */
  get context() {
    const value = this.connectionInfo$.getValue();
    if (value) {
      const processor = value.processor?.name;
      return processor ? `${value.instance.name}__${processor}` : value.instance.name;
    }
  }

  /**
   * Returns the currently active instance (if any).
   */
  get instance() {
    return this.connectionInfo$.getValue()?.instance?.name;
  }

  /**
   * Returns the currently active processor (if any).
   */
  get processor() {
    return this.connectionInfo$.getValue()?.processor?.name;
  }

  clearContext() {
    this.connectionInfo$.next(null);
    this.time$.next(null);
    this.clearance$.next(null);
    this.timeSubscription?.cancel();
    this.clearanceSubscription?.cancel();
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

  /**
   * Returns lookback period
   */
  getTimeRange() {
    return this.range$.value;
  }
}
