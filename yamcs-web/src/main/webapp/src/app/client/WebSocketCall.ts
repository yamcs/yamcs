import { ServerMessage, WebSocketClient } from './WebSocketClient';

/**
 * A single API call on a WebSocket client.
 * Multiple calls may be active on the same WebSocket connection.
 */
export class WebSocketCall<O, D> {

  private _id?: number;

  constructor(
    private client: WebSocketClient,
    private requestId: number,
    private type: string,
    private observer: (data: D) => void,
  ) { }

  /**
   * Returns the server-assigned unique call id
   */
  get id() {
    return this._id;
  }

  sendMessage(options: O) {
    this.client.sendMessage({
      type: this.type,
      id: this.requestId,
      options,
    });
  }

  consume(msg: ServerMessage) {
    // Yamcs will always send a reply before any other call-related data
    if (msg.type === 'reply' && msg.data.replyTo === this.requestId) {
      this._id = msg.call;
      if (msg.data.exception) {
        const errCode = msg.data.exception['code'];
        const errType = msg.data.exception['type'];
        const errDetail = msg.data.exception['msg'];
        console.error(`Received ${errCode} ${errType} for topic '${this.type}': ${errDetail}`);
      }
    } else if (msg.type === this.type && msg.call === this.id) {
      this.observer(msg.data);
    }
  }

  cancel() {
    this.client.cancelCall(this);
  }
}
