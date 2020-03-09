import { Observable } from 'rxjs';
import { filter, first, map } from 'rxjs/operators';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { WebSocketServerMessage } from './types/internal';
import { Processor, ProcessorSubscriptionRequest, ProcessorSubscriptionResponse } from './types/system';

const PROTOCOL_VERSION = 1;
const MESSAGE_TYPE_REQUEST = 1;
const MESSAGE_TYPE_REPLY = 2;
const MESSAGE_TYPE_EXCEPTION = 3;
const MESSAGE_TYPE_DATA = 4;

export class WebSocketClient {

  private webSocket: WebSocketSubject<{}>;

  private webSocketConnection$: Observable<{}>;

  private requestSequence = 0;

  constructor(baseHref: string, instance?: string) {
    const currentLocation = window.location;
    let url = 'ws://';
    if (currentLocation.protocol === 'https:') {
      url = 'wss://';
    }
    url += `${currentLocation.host}${baseHref}_websocket`;
    if (instance) {
      url += `/${instance}`;
    }

    this.webSocket = webSocket({
      url,
      protocol: 'json',
    });
    this.webSocketConnection$ = this.webSocket.pipe();
  }

  async getProcessorUpdates(options?: ProcessorSubscriptionRequest) {
    const requestId = this.emit({ processor: 'subscribe', data: options });

    return new Promise<ProcessorSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = msg[3].data as ProcessorSubscriptionResponse;
          response.processor$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'PROCESSOR_INFO'),
            map(msg => msg[3].data as Processor),
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
    this.webSocket.unsubscribe();
  }

  private emit(payload: { [key: string]: any, data?: {}; }) {
    this.webSocket.next([
      PROTOCOL_VERSION,
      MESSAGE_TYPE_REQUEST,
      ++this.requestSequence,
      payload,
    ]);
    return this.requestSequence;
  }
}
