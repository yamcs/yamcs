import { Pipe, PipeTransform } from '@angular/core';
import { CommandId } from '../../client';
import * as utils from '../../shared/utils';

@Pipe({ name: 'commandId' })
export class CommandIdPipe implements PipeTransform {

  transform(commandId?: CommandId): string | null {
    if (!commandId) {
      return null;
    }
    return utils.printCommandId(commandId);
  }
}
