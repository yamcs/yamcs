import { MessageService, YamcsService } from '@yamcs/webapp-sdk';

/**
 * Library added to the scope of OPI scripts under the name 'Yamcs'.
 */
export class YamcsScriptLibrary {

  constructor(
    private yamcs: YamcsService,
    private messageService: MessageService,
  ) { }

  issueCommand(qname: string, args?: { [key: string]: any; }) {
    this.yamcs.yamcsClient.issueCommand(this.yamcs.instance!, this.yamcs.processor!, qname, {
      args,
    }).catch(err => this.messageService.showError(err));
  }

  runProcedure(procedure: string, args?: { [key: string]: any; }) {
    const strArgs: { [key: string]: string; } = {};
    if (args) {
      Object.keys(args).forEach(k => strArgs[k] = String(args[k]));
    }
    this.yamcs.yamcsClient.startProcedure(this.yamcs.instance!, procedure, {
      arguments: strArgs,
    }).catch(err => this.messageService.showError(err));
  }
}
