import { Pipe, PipeTransform } from '@angular/core';
import { CommandHistoryRecord, YamcsService } from '@yamcs/webapp-sdk';

@Pipe({
  standalone: true,
  name: 'commandDownloadLink',
})
export class CommandDownloadLinkPipe implements PipeTransform {

  constructor(private yamcs: YamcsService) {
  }

  transform(command: CommandHistoryRecord | null): string | null {
    if (!command) {
      return null;
    }

    const instance = this.yamcs.instance!;
    return this.yamcs.yamcsClient.getCommandDownloadURL(instance, command.id);
  }
}
