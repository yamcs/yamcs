import { AlarmSubscriptionResponse, EventSubscriptionResponse, ParameterSubscriptionRequest, ParameterSubscriptionResponse } from './types/monitoring';
import { CommandQueueEventSubscriptionResponse, CommandQueueSubscriptionResponse, CommandSubscriptionRequest, CommandSubscriptionResponse, LinkSubscriptionResponse, ProcessorSubscriptionResponse, StatisticsSubscriptionResponse, StreamEventSubscriptionResponse, StreamSubscriptionResponse } from './types/system';
import { WebSocketClient } from './WebSocketClient';
import YamcsClient from './YamcsClient';

export class InstanceClient {

  private webSocketClient?: WebSocketClient;

  constructor(
    readonly instance: string,
    private yamcs: YamcsClient) {
  }

  async getEventUpdates(): Promise<EventSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getEventUpdates();
  }

  async unsubscribeEventUpdates() {
    return this.webSocketClient!.unsubscribeEventUpdates();
  }

  async getLinkUpdates(): Promise<LinkSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getLinkUpdates(this.instance);
  }

  async unsubscribeLinkUpdates() {
    return this.webSocketClient!.unsubscribeLinkUpdates();
  }

  async getStreamEventUpdates(): Promise<StreamEventSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getStreamEventUpdates(this.instance);
  }

  async unsubscribeStreamEventUpdates() {
    return this.webSocketClient!.unsubscribeStreamEventUpdates();
  }

  async getProcessorUpdates(): Promise<ProcessorSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getProcessorUpdates();
  }

  async getProcessorStatistics(): Promise<StatisticsSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getProcessorStatistics(this.instance);
  }

  async getCommandUpdates(options: CommandSubscriptionRequest = {}): Promise<CommandSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getCommandUpdates(options);
  }

  async unsubscribeCommandUpdates() {
    return this.webSocketClient!.unsubscribeCommandUpdates();
  }

  async getCommandQueueUpdates(processorName?: string): Promise<CommandQueueSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getCommandQueueUpdates(this.instance, processorName);
  }

  async getCommandQueueEventUpdates(processorName?: string): Promise<CommandQueueEventSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getCommandQueueEventUpdates(this.instance, processorName);
  }

  async getAlarmUpdates(): Promise<AlarmSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getAlarmUpdates({
      detail: true,
    });
  }

  async getStreamUpdates(name: string): Promise<StreamSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getStreamUpdates(name);
  }

  async getParameterValueUpdates(options: ParameterSubscriptionRequest): Promise<ParameterSubscriptionResponse> {
    this.prepareWebSocketClient();
    return this.webSocketClient!.getParameterValueUpdates(options);
  }

  async unsubscribeParameterValueUpdates(options: ParameterSubscriptionRequest) {
    return this.webSocketClient!.unsubscribeParameterValueUpdates(options);
  }

  async unsubscribeStreamUpdates() {
    return this.webSocketClient!.unsubscribeStreamUpdates();
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
