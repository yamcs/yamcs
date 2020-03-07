import { ParameterSubscriptionRequest, ParameterSubscriptionResponse } from './types/monitoring';
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

  async getParameterValueUpdates(options: ParameterSubscriptionRequest): Promise<ParameterSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getParameterValueUpdates(options);
  }

  async unsubscribeParameterValueUpdates(options: ParameterSubscriptionRequest) {
    return this.webSocketClient!.unsubscribeParameterValueUpdates(options);
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
