import { ArgumentAssignment } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

/**
 * Library added to the scope of OPI scripts under the name 'Yamcs'.
 */
export class YamcsScriptLibrary {

    constructor(private yamcs: YamcsService, private instance: string) {
    }

    issueCommand(qname: string, args: { [key: string]: any }) {
        const processor = this.yamcs.getProcessor();
        const instanceClient = this.yamcs.getInstanceClient()!;

        const assignments: ArgumentAssignment[] = [];
        for (const name in args) {
            assignments.push({ name, value: args[name] });
        }

        instanceClient.issueCommand(processor.name, qname, {
            assignment: assignments,
        });
    }
}
