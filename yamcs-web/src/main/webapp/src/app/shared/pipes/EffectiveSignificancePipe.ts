import { Pipe, PipeTransform } from '@angular/core';
import { Command, Significance } from '../../client';

@Pipe({ name: 'effectiveSignificance' })
export class EffectiveSignificancePipe implements PipeTransform {

  transform(command: Command | null | undefined): Significance | null {
    if (!command) {
      return null;
    }

    if (command.significance) {
      return command.significance;
    }
    let node = command;
    while (node.baseCommand) {
      node = node.baseCommand;
      if (node.significance) {
        return node.significance;
      }
    }
    return null;
  }
}
