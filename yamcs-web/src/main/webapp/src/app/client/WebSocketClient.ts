import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, first, map } from 'rxjs/operators';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { WebSocketServerMessage } from './types/internal';
import { Alarm, AlarmSubscriptionResponse, CommandHistoryEntry, Event, EventSubscriptionResponse, ParameterData, ParameterSubscriptionRequest, ParameterSubscriptionResponse } from './types/monitoring';
import { AlarmSubscriptionRequest, CommandQueue, CommandQueueEvent, CommandQueueEventSubscriptionResponse, CommandQueueSubscriptionResponse, CommandSubscriptionRequest, CommandSubscriptionResponse, ConnectionInfo, LinkEvent, LinkSubscriptionResponse, Processor, ProcessorSubscriptionRequest, ProcessorSubscriptionResponse, Statistics, StatisticsSubscriptionResponse, StreamData, StreamEvent, StreamEventSubscriptionResponse, StreamSubscriptionResponse } from './types/system';

const PROTOCOL_VERSION = 1;
const MESSAGE_TYPE_REQUEST = 1;
const MESSAGE_TYPE_REPLY = 2;
const MESSAGE_TYPE_EXCEPTION = 3;
const MESSAGE_TYPE_DATA = 4;

export class WebSocketClient {

  private connected$ = new BehaviorSubject<boolean>(false);

  private webSocket: WebSocketSubject<{}>;

  private webSocketConnection$: Observable<{}>;
  private webSocketConnectionSubscription: Subscription;

  // Server-controlled metadata on the connected client
  // (instance, processor)
  private connectionInfo$ = new BehaviorSubject<ConnectionInfo | null>(null);

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
      closeObserver: {
        next: () => {
          this.connected$.next(false);
        }
      },
      openObserver: {
        next: () => {
          // Note we do not set connected$ here
          // Instead prefer to set that after
          // receiving the initial bootstrap message
        }
      }
    });
    this.webSocketConnection$ = this.webSocket.pipe(
      // retryWhen(errors => {
      //  console.log('Cannot connect to Yamcs');
      //  return errors.pipe(delay(1000));
      //}),
    );
    this.webSocketConnectionSubscription = this.webSocketConnection$.subscribe(
      (msg: WebSocketServerMessage) => {
        if (!this.connected$.value && msg[1] === MESSAGE_TYPE_DATA && msg[3].dt === 'CONNECTION_INFO') {
          const connectionInfo = msg[3].data as ConnectionInfo;
          this.connectionInfo$.next(connectionInfo);
          this.connected$.next(true);
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          console.error(`Server error:  ${msg[3].et}`, msg[3].msg);
        }
      },
      (err: any) => console.log(err)
    );
  }

  async getEventUpdates() {
    const requestId = this.emit({ events: 'subscribe' });

    return new Promise<EventSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as EventSubscriptionResponse;
          response.event$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'EVENT'),
            map(msg => msg[3].data as Event),
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

  async unsubscribeEventUpdates() {
    const requestId = this.emit({ events: 'unsubscribe' });

    return new Promise<void>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          resolve();
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getLinkUpdates(instance?: string) {
    const requestId = this.emit({ links: 'subscribe' });

    return new Promise<LinkSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as LinkSubscriptionResponse;
          response.linkEvent$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'LINK_EVENT'),
            map(msg => msg[3].data as LinkEvent),
            filter((linkEvent: LinkEvent) => {
              return !instance || (instance === linkEvent.linkInfo.instance);
            }),
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

  async unsubscribeLinkUpdates() {
    const requestId = this.emit({ links: 'unsubscribe' });

    return new Promise<void>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          resolve();
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getStreamEventUpdates(instance: string) {
    const requestId = this.emit({
      streams: 'subscribe',
      data: { instance },
    });

    return new Promise<StreamEventSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as StreamEventSubscriptionResponse;
          response.streamEvent$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'STREAM_EVENT'),
            map(msg => msg[3].data as StreamEvent),
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

  async unsubscribeStreamEventUpdates() {
    const requestId = this.emit({
      streams: 'unsubscribe',
    });

    return new Promise<void>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          resolve();
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getAlarmUpdates(options?: AlarmSubscriptionRequest) {
    const requestId = this.emit({ alarms: 'subscribe', data: options });

    return new Promise<AlarmSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as AlarmSubscriptionResponse;
          response.alarm$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt!.endsWith('ALARM_DATA')),
            map(msg => msg[3].data as Alarm),
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

  async unsubscribeStreamUpdates() {
    const requestId = this.emit({
      stream: 'unsubscribe',
    });
    return new Promise<void>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          resolve();
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async getStreamUpdates(stream: string) {
    const requestId = this.emit({
      stream: 'subscribe',
      data: { stream },
    });

    return new Promise<StreamSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as StreamSubscriptionResponse;
          response.streamData$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'STREAM_DATA'),
            map(msg => msg[3].data as StreamData),
            filter(streamData => streamData.stream === stream),
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

  async getProcessorStatistics(instance?: string) {
    const requestId = this.emit({
      management: 'subscribe',
    });

    return new Promise<StatisticsSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as StatisticsSubscriptionResponse;
          response.statistics$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'PROCESSING_STATISTICS'),
            map(msg => msg[3].data as Statistics),
            filter((statistics: Statistics) => {
              return !instance || (instance === statistics.instance);
            }),
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

  async getCommandUpdates(options: CommandSubscriptionRequest = {}) {
    const requestId = this.emit({
      cmdhistory: 'subscribe',
      data: options,
    });

    return new Promise<CommandSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as CommandSubscriptionResponse;
          response.command$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'CMD_HISTORY'),
            map(msg => msg[3].data as CommandHistoryEntry),
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

  async getCommandQueueUpdates(instance?: string, processor?: string) {
    const requestId = this.emit({ cqueues: 'subscribe' });

    return new Promise<CommandQueueSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as CommandQueueSubscriptionResponse;
          response.commandQueue$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'COMMAND_QUEUE_INFO'),
            map(msg => msg[3].data as CommandQueue),
            filter((commandQueue: CommandQueue) => {
              return !instance || (instance === commandQueue.instance);
            }),
            filter((commandQueue: CommandQueue) => {
              return !processor || (processor === commandQueue.processorName);
            }),
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

  async getCommandQueueEventUpdates(instance?: string, processor?: string) {
    const requestId = this.emit({ cqueues: 'subscribe' });

    return new Promise<CommandQueueEventSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = {} as CommandQueueEventSubscriptionResponse;
          response.commandQueueEvent$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'COMMAND_QUEUE_EVENT'),
            map(msg => msg[3].data as CommandQueueEvent),
            filter((commandQueueEvent: CommandQueueEvent) => {
              return !instance || (instance === commandQueueEvent.data.instance);
            }),
            filter((commandQueueEvent: CommandQueueEvent) => {
              return !processor || (processor === commandQueueEvent.data.processorName);
            }),
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

  async getParameterValueUpdates(options: ParameterSubscriptionRequest) {
    const requestId = this.emit({
      parameter: 'subscribe',
      data: options,
    });

    return new Promise<ParameterSubscriptionResponse>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          const response = msg[3].data as ParameterSubscriptionResponse;

          // Turn SubscribedParameters into a more convenient mapping
          response.mapping = {};
          if (response.subscribed) {
            for (const subscribedParameter of response.subscribed) {
              response.mapping[subscribedParameter.numericId] = subscribedParameter.id;
            }
          }

          response.parameterValues$ = this.webSocketConnection$.pipe(
            filter((msg: WebSocketServerMessage) => msg[1] === MESSAGE_TYPE_DATA),
            filter((msg: WebSocketServerMessage) => msg[3].dt === 'PARAMETER'),
            map(msg => msg[3].data as ParameterData),
            filter(pdata => pdata.subscriptionId === response.subscriptionId),
            map(pdata => pdata.parameter),
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

  async unsubscribeParameterValueUpdates(options: ParameterSubscriptionRequest) {
    const requestId = this.emit({
      parameter: 'unsubscribe',
      data: options,
    });

    return new Promise<void>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          resolve();
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  async unsubscribeCommandUpdates() {
    const requestId = this.emit({
      cmdhistory: 'unsubscribe',
    });

    return new Promise<void>((resolve, reject) => {
      this.webSocketConnection$.pipe(
        first((msg: WebSocketServerMessage) => {
          return msg[2] === requestId && msg[1] !== MESSAGE_TYPE_DATA;
        }),
      ).subscribe((msg: WebSocketServerMessage) => {
        if (msg[1] === MESSAGE_TYPE_REPLY) {
          resolve();
        } else if (msg[1] === MESSAGE_TYPE_EXCEPTION) {
          reject(msg[3].et);
        } else {
          reject('Unexpected response code');
        }
      });
    });
  }

  close() {
    this.webSocketConnectionSubscription.unsubscribe();
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
