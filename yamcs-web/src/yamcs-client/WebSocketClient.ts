import { SubscriptionModel } from './SubscriptionModel';
import { webSocket } from 'rxjs/observable/dom/webSocket';
import { delay, filter, map, retryWhen } from 'rxjs/operators';
import { WebSocketSubject } from 'rxjs/observable/dom/WebSocketSubject';
import { WebSocketServerMessage } from './types/internal';
import {
  LinkEvent,
  ParameterData,
  TimeInfo,
  ParameterSubscriptionRequest,
} from './types/main';
import { Observable } from 'rxjs/Observable';

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
  private webSocketObservable: Observable<{}>;

  private requestSequence = 0;

  // Toggle to distinguish original open from a reconnection.
  private subscribeOnOpen = false;

  constructor(instance: string) {
    this.subscriptionModel = new SubscriptionModel();
    this.webSocket = webSocket({
      url: `ws://localhost:8090/_websocket/${instance}`,
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
    this.webSocketObservable = this.webSocket.pipe(
      retryWhen(errors => {
        console.log('Cannot connect to Yamcs');
        return errors.pipe(delay(1000));
      }),
    );
    this.webSocketObservable.subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          console.error('Server reported error: ', msg[3].msg);
        }
      },
      (err) => console.log(err),
      () => console.log('complete'),
    );
  }

  getTimeUpdates() {
    this.subscriptionModel.time = true;
    this.emit({ time: 'subscribe' });
    return this.webSocketObservable.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'TIME_INFO'),
      map(msg => msg[3].data as TimeInfo),
    );
  }

  getLinkUpdates() {
    this.subscriptionModel.links = true;
    this.emit({ links: 'subscribe' });
    return this.webSocketObservable.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'LINK_EVENT'),
      map(msg => msg[3].data as LinkEvent),
    );
  }

  getParameterValueUpdates(options: ParameterSubscriptionRequest) {
    this.subscriptionModel.parameters = options;
    this.emit({
      parameter: 'subscribe',
      data: options,
    });
    return this.webSocketObservable.pipe(
      filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
      filter((msg: WebSocketServerMessage) => msg[3].dt === 'PARAMETER'),
      map(msg => msg[3].data as ParameterData),
    );
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
    if (this.subscriptionModel.time) {
      this.emit({ time: 'subscribe' });
    }
    if (this.subscriptionModel.links) {
      this.emit({ links: 'subscribe' });
    }
    if (this.subscriptionModel.parameters) {
      this.emit({ parameters: 'subscribe', data: this.subscriptionModel.parameters });
    }
  }
}
