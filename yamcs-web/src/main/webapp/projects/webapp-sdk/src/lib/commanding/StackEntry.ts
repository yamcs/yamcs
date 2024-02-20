import { Command, Value } from '../client';
import { AdvancementParams } from './AdvancementParams';
import { CommandHistoryRecord } from './CommandHistoryRecord';

export interface StackEntry {
  name: string;
  namespace?: string;
  args: { [key: string]: any; };
  comment?: string;
  extra?: { [key: string]: Value; };
  advancement?: AdvancementParams;

  command?: Command;

  executionNumber?: number;
  executing?: boolean;
  id?: string;
  record?: CommandHistoryRecord;
  err?: string;
}
