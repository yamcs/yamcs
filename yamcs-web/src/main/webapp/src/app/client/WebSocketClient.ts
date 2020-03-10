import { BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { WebSocketCall } from './WebSocketCall';

export type ClientMessage = {
  type: string;
  options: any;
  id?: number;
  call?: number;
};

export type ServerMessage = {
  type: string;
  call: number;
  seq: number;
  data: any;
};

export class WebSocketClient {

  readonly connected$ = new BehaviorSubject<boolean>(false);

  private webSocket$: WebSocketSubject<{}>; // Unsubscribing from this closes the connection
  private calls: Array<WebSocketCall<any, any>> = [];

  private requestSequence = 0;

  constructor(apiUrl: string) {
    const currentLocation = window.location;
    let url = 'ws://';
    if (currentLocation.protocol === 'https:') {
      url = 'wss://';
    }
    url += `${currentLocation.host}${apiUrl}/websocket`;

    this.webSocket$ = webSocket({
      url,
      protocol: 'json',
      closeObserver: {
        next: () => this.connected$.next(false)
      },
      openObserver: {
        next: () => this.connected$.next(true)
      }
    });
    //this.webSocketConnection$ = this.webSocket.pipe(
    // retryWhen(errors => {
    //  console.log('Cannot connect to Yamcs');
    //  return errors.pipe(delay(1000));
    //}),
    //);

    this.webSocket$.pipe(
      tap((msg: ServerMessage) => {
        this.calls.forEach(call => call.consume(msg));
      })
    ).subscribe();
  }

  createSubscription<O, D>(type: string, options: O, observer: (data: D) => void) {
    const id = ++this.requestSequence;
    const call = new WebSocketCall(this, id, type, observer);
    this.calls.push(call);
    this.sendMessage({ type, id, options });
    return call;
  }

  sendMessage(clientMessage: ClientMessage) {
    this.webSocket$.next(clientMessage);
  }

  cancelCall(call: WebSocketCall<any, any>) {
    const idx = this.calls.indexOf(call);
    if (idx !== -1) {
      this.calls.splice(idx, 1);
    }
    if (call.id !== undefined && this.connected$.value) {
      this.sendMessage({
        type: 'cancel',
        options: { call: call.id },
      });
    }
  }

  close() {
    this.calls.length = 0;
    this.webSocket$.unsubscribe();
  }
}
