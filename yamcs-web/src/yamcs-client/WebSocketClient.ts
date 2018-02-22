import { SubscriptionModel } from './SubscriptionModel';
import { webSocket } from 'rxjs/observable/dom/webSocket';
import { delay, filter, map, retryWhen } from 'rxjs/operators';
import { WebSocketSubject } from 'rxjs/observable/dom/WebSocketSubject';
import { WebSocketServerMessage } from './types/internal';
import {
  Event,
  ParameterData,
  TimeInfo,
  ParameterSubscriptionRequest,
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

const PROTOCOL_VERSION = 1;
const MESSAGE_TYPE_REQUEST = 1;
// const MESSAGE_TYPE_REPLY = 2;
const MESSAGE_TYPE_EXCEPTION = 3;
const MESSAGE_TYPE_DATA = 4;

/**
 * Automatically reconnecting web socket client. It also
 * transfers subscriptions between different connections.
 */
export class WebSocketClient {

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
        next: () => this.subscribeOnOpen = true
      },
      openObserver: {
        next: () => {
          console.log('Connected to Yamcs');
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
    this.webSocketConnection$.subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          console.error('Server reported error: ', msg[3].msg);
        }
      },
      (err) => console.log(err),
      () => console.log('complete'),
    );
  }

  getEventUpdates() {
    if (!this.subscriptionModel.events) {
      this.subscriptionModel.events = true;
      this.emit({ events: 'subscribe' });
    }
    return this.webSocketConnection$.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'EVENT'),
      map(msg => msg[3].data as Event),
    );
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
    this.emit({
      parameter: 'subscribe',
      data: options,
    });
    return this.webSocketConnection$.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'PARAMETER'),
      map(msg => msg[3].data as ParameterData),
    );
  }

  close() {
    this.webSocketConnectionSubscription.unsubscribe();
    this.webSocket.unsubscribe();
  }

  private emit(payload: { [key: string]: any, data?: {} }) {
    this.webSocket.next(JSON.stringify([
      PROTOCOL_VERSION,
      MESSAGE_TYPE_REQUEST,
      this.requestSequence,
      payload,
    ]));
  }

  private registerSubscriptions() {
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
      this.emit({ parameters: 'subscribe', data: this.subscriptionModel.parameters });
    }
    if (this.subscriptionModel.time) {
      this.emit({ time: 'subscribe' });
    }
  }
}
