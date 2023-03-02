import { Command, Value } from '../../client';
import { CommandHistoryRecord } from '../command-history/CommandHistoryRecord';

export interface StackEntry {
  name: string;
  args: { [key: string]: any; };
  comment?: string;
  extra?: { [key: string]: Value; };
  advancement?: AdvancementParams;

  command?: Command;

  executionNumber?: number;
  executing: boolean;
  id?: string;
  record?: CommandHistoryRecord;
  err?: string;
}

export interface AdvancementParams {
  acknowledgment?: string;
  wait?: number;
}
