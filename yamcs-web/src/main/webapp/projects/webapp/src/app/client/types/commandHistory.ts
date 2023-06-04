import { WebSocketCall } from '../WebSocketCall';
import { CommandHistoryEntry } from './monitoring';

export interface SubscribeCommandsRequest {
  instance: string;
  processor: string;
  ignorePastCommands?: boolean;
}

export type CommandSubscription = WebSocketCall<SubscribeCommandsRequest, CommandHistoryEntry>;
