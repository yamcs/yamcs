import { SubscriptionModel } from './SubscriptionModel';
import { webSocket } from 'rxjs/observable/dom/webSocket';
import { delay, filter, map, retryWhen, first } from 'rxjs/operators';
import { WebSocketSubject } from 'rxjs/observable/dom/WebSocketSubject';
import { WebSocketServerMessage } from './types/internal';
import {
  Alarm,
  Event,
  ParameterData,
  TimeInfo,
  ParameterSubscriptionRequest,
  ParameterSubscriptionResponse,
  EventSubscriptionResponse,
} from './types/monitoring';
import {
  ClientInfo,
  LinkEvent,
  Processor,
  Statistics,
  CommandQueue,
  CommandQueueEvent,
} from './types/system';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';

const PROTOCOL_VERSION = 1;
const MESSAGE_TYPE_REQUEST = 1;
const MESSAGE_TYPE_REPLY = 2;
const MESSAGE_TYPE_EXCEPTION = 3;
const MESSAGE_TYPE_DATA = 4;

/**
 * Automatically reconnecting web socket client. It also
 * transfers subscriptions between different connections.
 */
export class WebSocketClient {

  readonly connected$ = new BehaviorSubject<boolean>(false);

  private subscriptionModel: SubscriptionModel;
  private webSocket: WebSocketSubject<{}>;

  private webSocketConnection$: Observable<{}>;
  private webSocketConnectionSubscription: Subscription;

  private requestSequence = 0;

  // Toggle to distinguish original open from a reconnection.
  private subscribeOnOpen = false;

  constructor(instance: string) {
    const currentLocation = window.location;
    let wsUrl = 'ws://';
    if (currentLocation.protocol === 'https') {
      wsUrl = 'wss://';
    }
    wsUrl += `${currentLocation.host}/_websocket/${instance}`;

    this.subscriptionModel = new SubscriptionModel();
    this.webSocket = webSocket({
      url: wsUrl,
      closeObserver: {
        next: () => {
          this.connected$.next(false);
          this.subscribeOnOpen = true;
        }
      },
      openObserver: {
        next: () => {
          console.log('Connected to Yamcs');
          this.connected$.next(true);
          if (this.subscribeOnOpen) {
            this.registerSubscriptions();
          }
        }
      }
    });
    this.webSocketConnection$ = this.webSocket.pipe(
      retryWhen(errors => {
        console.log('Cannot connect to Yamcs');
        return errors.pipe(delay(1000));
      }),
    );
    this.webSocketConnectionSubscription = this.webSocketConnection$.subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          console.error(`Server error:  ${msg[3].et}`, msg[3].msg);
        }
      },
      (err) => console.log(err),
      () => console.log('complete'),
    );
  }

  getEventUpdates() {
    this.subscriptionModel.events = true;
    const requestId = this.emit({ events: 'subscribe' });

    return new Promise<EventSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as EventSubscriptionResponse;
          response.event$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'EVENT'),
            map(msg => msg[3].data as Event),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      })
    });
  }

  getTimeUpdates() {
    if (!this.subscriptionModel.time) {
      this.subscriptionModel.time = true;
      this.emit({ time: 'subscribe' });
    }
    return this.webSocketConnection$.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'TIME_INFO'),
      map(msg => msg[3].data as TimeInfo),
    );
  }

  getLinkUpdates() {
    if (!this.subscriptionModel.links) {
      this.subscriptionModel.links = true;
      this.emit({ links: 'subscribe' });
    }
    return this.webSocketConnection$.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'LINK_EVENT'),
      map(msg => msg[3].data as LinkEvent),
    );
  }

  getAlarmUpdates() {
    if (!this.subscriptionModel.alarms) {
      this.subscriptionModel.alarms = true;
      this.emit({ alarms: 'subscribe' });
    }
    return this.webSocketConnection$.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'ALARM_DATA'),
      map(msg => msg[3].data as Alarm),
    );
  }

  getClientUpdates() {
    if (!this.subscriptionModel.management) {
      this.subscriptionModel.management = true;
      this.emit({ management: 'subscribe' });
    }
    return this.webSocketConnection$.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'CLIENT_INFO'),
      map(msg => msg[3].data as ClientInfo),
    );
  }

  getProcessorUpdates() {
    if (!this.subscriptionModel.management) {
      this.subscriptionModel.management = true;
      this.emit({ management: 'subscribe' });
    }
    return this.webSocketConnection$.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'PROCESSOR_INFO'),
      map(msg => msg[3].data as Processor),
    );
  }

  getProcessorStatistics() {
    if (!this.subscriptionModel.management) {
      this.subscriptionModel.management = true;
      this.emit({ management: 'subscribe' });
    }
    return this.webSocketConnection$.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'PROCESSING_STATISTICS'),
      map(msg => msg[3].data as Statistics),
    );
  }

  getCommandQueueUpdates() {
    if (!this.subscriptionModel.commandQueues) {
      this.subscriptionModel.commandQueues = true;
      this.emit({ cqueues: 'subscribe' });
    }
    return this.webSocketConnection$.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'COMMAND_QUEUE_INFO'),
      map(msg => msg[3].data as CommandQueue),
    );
  }

  getCommandQueueEventUpdates() {
    if (!this.subscriptionModel.commandQueues) {
      this.subscriptionModel.commandQueues = true;
      this.emit({ cqueues: 'subscribe' });
    }
    return this.webSocketConnection$.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'COMMAND_QUEUE_EVENT'),
      map(msg => msg[3].data as CommandQueueEvent),
    );
  }

  getParameterValueUpdates(options: ParameterSubscriptionRequest) {
    this.subscriptionModel.parameters = options;
    const requestId = this.emit({
      parameter: 'subscribe',
      data: options,
    });

    return new Promise<ParameterSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = msg[3].data as ParameterSubscriptionResponse;
          response.parameterValues$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'PARAMETER'),
            map(msg => msg[3].data as ParameterData),
            filter(pdata => pdata.subscriptionId === response.subscriptionId),
            map(pdata => pdata.parameter),
          );
          resolve(response);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  close() {
    this.webSocketConnectionSubscription.unsubscribe();
    this.webSocket.unsubscribe();
  }

  private emit(payload: { [key: string]: any, data?: {} }) {
    this.webSocket.next(JSON.stringify([
      PROTOCOL_VERSION,
      MESSAGE_TYPE_REQUEST,
      ++this.requestSequence,
      payload,
    ]));
    return this.requestSequence
  }

  private registerSubscriptions() {
    if (this.subscriptionModel.alarms) {
      this.emit({ alarms: 'subscribe' });
    }
    if (this.subscriptionModel.events) {
      this.emit({ events: 'subscribe' });
    }
    if (this.subscriptionModel.links) {
      this.emit({ links: 'subscribe' });
    }
    if (this.subscriptionModel.management) {
      this.emit({ management: 'subscribe' });
    }
    if (this.subscriptionModel.commandQueues) {
      this.emit({ cqueues: 'subscribe' });
    }
    if (this.subscriptionModel.parameters) {
      this.emit({ parameter: 'subscribe', data: this.subscriptionModel.parameters });
    }
    if (this.subscriptionModel.time) {
      this.emit({ time: 'subscribe' });
    }
  }
}
