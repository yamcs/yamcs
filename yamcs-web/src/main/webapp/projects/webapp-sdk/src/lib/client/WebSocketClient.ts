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

// Reconnect backoff bounds (ms). A dropped WebSocket (tab suspend, wifi
// blip, brief server restart, ...) should not require a manual page
// reload to recover from.
const RECONNECT_MIN_DELAY = 1000;
const RECONNECT_MAX_DELAY = 30000;

export class WebSocketClient {
  readonly connected$ = new BehaviorSubject<boolean>(false);

  private url: string;
  private webSocket$: WebSocketSubject<{}>; // Unsubscribing from this closes the connection
  private calls: Array<WebSocketCall<any, any>> = [];

  private requestSequence = 0;

  private manuallyClosed = false;
  private reconnectAttempt = 0;
  private reconnectTimer?: ReturnType<typeof setTimeout>;

  constructor(
    apiUrl: string,
    private frameLossListener: FrameLossListener,
  ) {
    const currentLocation = window.location;
    let url = 'ws://';
    if (currentLocation.protocol === 'https:') {
      url = 'wss://';
    }
    url += `${currentLocation.host}${apiUrl}/websocket`;
    this.url = url;

    this.connect();
  }

  private connect() {
    this.webSocket$ = webSocket({
      url: this.url,
      protocol: 'json',
      closeObserver: {
        next: () => {
          this.connected$.next(false);
          this.scheduleReconnect();
        },
      },
      openObserver: {
        next: () => {
          this.reconnectAttempt = 0;
          // The server has no memory of this connection's previous calls.
          // Re-issue them all before announcing ourselves connected again.
          this.calls.forEach((call) => call.resubscribe());
          this.connected$.next(true);
        },
      },
    });

    this.webSocket$
      .pipe(
        tap((msg: ServerMessage) => {
          this.calls.forEach((call) => call.consume(msg));
        }),
      )
      .subscribe({
        // Prevent an unhandled-error console dump. closeObserver already
        // triggers the reconnect attempt for both clean and error closes.
        error: () => {},
      });
  }

  private scheduleReconnect() {
    if (this.manuallyClosed || this.reconnectTimer) {
      return;
    }
    const delay = Math.min(
      RECONNECT_MIN_DELAY * 2 ** this.reconnectAttempt,
      RECONNECT_MAX_DELAY,
    );
    this.reconnectAttempt++;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = undefined;
      this.connect();
    }, delay);
  }

  /**
   * Create a subscription on the active WebSocket connection. This should
   * be used for subscriptions where all server messages are expected to
   * be received. If we cannot read sufficiently fast, Yamcs will close the
   * entire connection (shared with other subscriptions).
   */
  createSubscription<O, D>(
    type: string,
    options: O,
    observer: (data: D) => void,
  ) {
    return this.doCreateSubscription(type, false, options, observer);
  }

  /**
   * Create a low-priority subscription on the active WebSocket connection. Yamcs may
   * drop WebSocket frames coming from this type of subscription, if we are not able
   * to read fast enough.
   */
  createLowPrioritySubscription<O, D>(
    type: string,
    options: O,
    observer: (data: D) => void,
  ) {
    return this.doCreateSubscription(type, true, options, observer);
  }

  private doCreateSubscription<O, D>(
    type: string,
    lowPriority: boolean,
    options: O,
    observer: (data: D) => void,
  ) {
    const id = ++this.requestSequence;
    const call = new WebSocketCall(this, id, type, lowPriority, options, observer);
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
    this.manuallyClosed = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }
    this.calls.length = 0;
    this.webSocket$.unsubscribe();
  }
}
