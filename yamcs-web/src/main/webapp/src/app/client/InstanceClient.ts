import { ProcessorSubscriptionResponse } from './types/system';
import { WebSocketClient } from './WebSocketClient';
import YamcsClient from './YamcsClient';

export class InstanceClient {

  private webSocketClient?: WebSocketClient;

  constructor(
    readonly instance: string,
    private yamcs: YamcsClient) {
  }

  async getProcessorUpdates(): Promise<ProcessorSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getProcessorUpdates();
  }

  closeConnection() {
    if (this.webSocketClient) {
      this.webSocketClient.close();
      this.webSocketClient = undefined;
    }
  }

  private prepareWebSocketClient() {
    if (!this.webSocketClient) {
      this.webSocketClient = new WebSocketClient(this.yamcs.baseHref, this.instance);
    }
  }
}
