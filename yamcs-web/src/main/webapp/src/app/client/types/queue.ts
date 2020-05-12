import { WebSocketCall } from '../WebSocketCall';
import { CommandId } from './monitoring';

export interface SubscribeQueueStatisticsRequest {
  instance: string;
  processor: string;
}

export interface SubscribeQueueEventsRequest {
  instance: string;
  processor: string;
}

export interface CommandQueueEntry {
  instance: string;
  processorName: string;
  queueName: string;
  cmdId: CommandId;
  source: string;
  binary: string;
  username: string;
  generationTime: string;
  uuid: string;
  pendingTransmissionConstraints: boolean;
}

export interface CommandQueue {
  instance: string;
  processorName: string;
  name: string;
  state: 'BLOCKED' | 'DISABLED' | 'ENABLED';
  users: string[];
  groups: string[];
  minLevel: string;
  nbSentCommands: number;
  nbRejectCommands: number;
  stateExpirationTimeS: number;
  entry: CommandQueueEntry[];
  order: number;
}

export interface CommandQueueEvent {
  type: 'COMMAND_ADDED' | 'COMMAND_UPDATED' | 'COMMAND_REJECTED' | 'COMMAND_SENT';
  data: CommandQueueEntry;
}

export interface EditCommandQueueOptions {
  state: 'enabled' | 'disabled' | 'blocked';
}

export interface EditCommandQueueEntryOptions {
  state: 'released' | 'rejected';
}

export type QueueStatisticsSubscription = WebSocketCall<SubscribeQueueStatisticsRequest, CommandQueue>;
export type QueueEventsSubscription = WebSocketCall<SubscribeQueueEventsRequest, CommandQueueEvent>;
