import { WebSocketCall } from '../WebSocketCall';
import { CommandAssignment } from './monitoring';

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
  id: string;
  commandName: string;
  origin: string;
  sequenceNumber: number;
  assignments: CommandAssignment[];
  binary: string;
  username: string;
  generationTime: string;
  pendingTransmissionConstraints: boolean;
}

export interface CommandQueue {
  instance: string;
  processorName: string;
  name: string;
  state: 'BLOCKED' | 'DISABLED' | 'ENABLED';
  users: string[];
  groups: string[];
  tcPatterns: string[];
  minLevel: string;
  entries: CommandQueueEntry[];
  acceptedCommandsCount: number;
  rejectedCommandsCount: number;
  order: number;
}

export interface CommandQueueEvent {
  type: 'COMMAND_ADDED' | 'COMMAND_UPDATED' | 'COMMAND_REJECTED' | 'COMMAND_SENT';
  data: CommandQueueEntry;
}

export type QueueStatisticsSubscription = WebSocketCall<SubscribeQueueStatisticsRequest, CommandQueue>;
export type QueueEventsSubscription = WebSocketCall<SubscribeQueueEventsRequest, CommandQueueEvent>;
