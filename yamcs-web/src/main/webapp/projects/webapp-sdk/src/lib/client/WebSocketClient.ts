import { BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { FrameLossListener } from './FrameLossListener';
import { WebSocketCall } from './WebSocketCall';

export type ClientMessage = {
  type: string;
  options: any;
  id?: number;
  call?: number;
  lowPriority?: boolean;
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

  constructor(apiUrl: string, private frameLossListener: FrameLossListener) {
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

    this.webSocket$.pipe(
      tap((msg: ServerMessage) => {
        this.calls.forEach(call => call.consume(msg));
      })
    ).subscribe();
  }

  /**
   * Create a subscription on the active WebSocket connection. This should
   * be used for subscriptions where all server messages are expected to
   * be received. If we cannot read sufficiently fast, Yamcs will close the
   * entire connection (shared with other subscriptions).
   */
  createSubscription<O, D>(type: string, options: O, observer: (data: D) => void) {
    return this.doCreateSubscription(type, false, options, observer);
  }

  /**
   * Create a low-priority subscription on the active WebSocket connection. Yamcs may
   * drop WebSocket frames coming from this type of subscription, if we are not able
   * to read fast enough.
   */
  createLowPrioritySubscription<O, D>(type: string, options: O, observer: (data: D) => void) {
    return this.doCreateSubscription(type, true, options, observer);
  }

  private doCreateSubscription<O, D>(type: string, lowPriority: boolean, options: O, observer: (data: D) => void) {
    const id = ++this.requestSequence;
    const call = new WebSocketCall(this, id, type, observer);
    call.addFrameLossListener(() => {
      this.frameLossListener.onFrameLoss();
    });
    this.calls.push(call);
    this.sendMessage({ type, id, lowPriority, options });
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
