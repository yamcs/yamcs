import { Command } from '../../client';

export interface CommandArgument {
    name: string;
    value: string;
}

export interface StackEntry {

    name: string;
    arguments: CommandArgument[];

    command?: Command;
}
