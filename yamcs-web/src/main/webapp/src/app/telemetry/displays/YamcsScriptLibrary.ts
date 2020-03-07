import { ArgumentAssignment } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';

/**
 * Library added to the scope of OPI scripts under the name 'Yamcs'.
 */
export class YamcsScriptLibrary {

  constructor(
    private yamcs: YamcsService,
    private messageService: MessageService,
    private instance: string,
  ) { }

  issueCommand(qname: string, args: { [key: string]: any; }) {
    const processor = this.yamcs.getProcessor();

    const assignments: ArgumentAssignment[] = [];
    for (const name in args) {
      assignments.push({ name, value: args[name] });
    }

    this.yamcs.yamcsClient.issueCommand(processor.instance, processor.name, qname, {
      assignment: assignments,
    }).catch(err => this.messageService.showError(err));
  }
}
