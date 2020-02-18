import { Command } from '../../client';
import { CommandHistoryRecord } from '../command-history/CommandHistoryRecord';

export interface CommandArgument {
    name: string;
    value: string;
}

export interface StackEntry {
    name: string;
    arguments: CommandArgument[];
    comment?: string;

    command?: Command;

    executionNumber?: number;
    id?: string;
    record?: CommandHistoryRecord;
    err?: string;
}
