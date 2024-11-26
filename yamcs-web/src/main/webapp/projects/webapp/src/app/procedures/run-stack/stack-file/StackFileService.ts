import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot } from '@angular/router';
import { AdvancementParams, Argument, Command, ConfigService, MessageService, StackFormatter, Step, StorageClient, utils, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, map } from 'rxjs';
import { StackedCheckEntry, StackedCommandEntry, StackedEntry, StackedTextEntry, StackedVerifyEntry } from './StackedEntry';
import { StackLogEntry } from './StackLogEntry';
import { parseXML } from './xmlparse';
import { parseYCS } from './ycsparse';

@Injectable()
export class StackFileService implements CanActivate {

  storageClient: StorageClient;
  bucket: string;
  objectName: string;

  entries: StackedEntry[] = [];

  advancement: AdvancementParams = {
    acknowledgment: 'Acknowledge_Queued',
    wait: 0,
  };

  dirty$ = new BehaviorSubject<boolean>(false);

  entries$ = new BehaviorSubject<StackedEntry[]>([]);
  hasState$ = this.entries$.pipe(
    map(entries => {
      for (const entry of entries) {
        if (entry.hasOutputs() || (entry.executionNumber !== undefined)) {
          return true;
        }
      }
      return false;
    })
  );
  logs$ = new BehaviorSubject<StackLogEntry[]>([]);

  constructor(
    private configService: ConfigService,
    private yamcs: YamcsService,
    private messageService: MessageService,
  ) {
    this.bucket = configService.getStackBucket();
    this.storageClient = yamcs.createStorageClient();
  }

  markDirty() {
    this.dirty$.next(true);
  }

  addLogEntry(executionNumber: number, text: string) {
    this.logs$.next([...this.logs$.value, {
      executionNumber,
      text,
      time: this.yamcs.getMissionTime().toISOString(),
    }]);
  }

  async canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    // Clear state
    this.entries = [];

    this.objectName = route.params['objectName'];
    const format = utils.getExtension(utils.getFilename(this.objectName))?.toLowerCase();

    try {
      const response = await this.storageClient.getObject(this.bucket, this.objectName);
      if (response.ok) {
        const text = await response.text();
        if (format === 'xml' || format === 'ycs') {
          await this.processStack(text, format);
        } else {
          return false;
        }
      }
    } catch (err: any) {
      this.messageService.showError(err);
      return false;
    }

    return true;
  }

  private async processStack(text: string, format: string) {
    this.entries = [];

    switch (format) {
      case 'ycs':
        let ycsEntries: Step[];
        [ycsEntries, this.advancement] = parseYCS(text, this.configService.getCommandOptions());
        for (const ycsEntry of ycsEntries) {
          if (ycsEntry.type === 'check') {
            this.entries.push(new StackedCheckEntry(ycsEntry));
          } else if (ycsEntry.type === 'command') {
            this.entries.push(new StackedCommandEntry(ycsEntry));
          } else if (ycsEntry.type === 'text') {
            this.entries.push(new StackedTextEntry(ycsEntry));
          } else if (ycsEntry.type === 'verify') {
            this.entries.push(new StackedVerifyEntry(ycsEntry));
          } else {
            console.error('Unexpected step', ycsEntry);
          }
        }
        break;
      case 'xml':
        const xmlModels = parseXML(text, this.configService.getCommandOptions());
        for (const xmlModel of xmlModels) {
          this.entries.push(new StackedCommandEntry(xmlModel));
        }
        break;
    }

    const commandEntries = this.entries.filter(entry => entry instanceof StackedCommandEntry);


    // Enrich entries with MDB info, it's used in the detail panel
    const promises = [];
    const instance = this.yamcs.instance!;
    for (const entry of commandEntries) {
      const namespace = entry.namespace ?? null;
      const name = entry.name;
      promises.push(this.yamcs.yamcsClient.getCommandForNamespace(instance, namespace, name).then(command => {
        entry.command = command;
      }));
    }

    // Wait on all definition requests to arrive
    for (const promise of promises) {
      try {
        await promise;
      } catch {
        // For now, don't care
      }
    }

    // Convert enum values to labels. This provides some resilience to MDB changes
    // where a numeric parameter becomes an enumeration.
    for (const entry of commandEntries) {
      if (entry.command) {
        for (const argumentName in entry.args) {
          const argument = this.getArgument(argumentName, entry.command);
          if (argument?.type.engType === 'enumeration') {
            let match = false;
            for (const enumValue of argument.type.enumValue || []) {
              if (enumValue.label === entry.args[argumentName]) {
                match = true;
                break;
              }
            }
            if (!match) {
              for (const enumValue of argument.type.enumValue || []) {
                if (String(enumValue.value) === String(entry.args[argumentName])) {
                  entry.args[argumentName] = enumValue.label;
                  match = true;
                  break;
                }
              }
            }
          }
        }
      }
    }

    // Convert arrays/aggregates from JSON to JavaScript
    if (format === 'xml') {
      for (const entry of commandEntries) {
        if (entry.command) {
          for (const argumentName in entry.args) {
            if (this.isComplex(argumentName, entry.command)) {
              entry.args[argumentName] = JSON.parse(entry.args[argumentName]);
            }
          }
        }
      }
    }
  }

  private getArgument(argumentName: string, info: Command): Argument | null {
    for (const argument of (info.argument || [])) {
      if (argument.name === argumentName) {
        return argument;
      }
    }
    if (info.baseCommand) {
      return this.getArgument(argumentName, info.baseCommand);
    } else {
      return null;
    }
  }

  private isComplex(argumentName: string, info: Command): boolean {
    for (const argument of (info.argument || [])) {
      if (argument.name === argumentName) {
        return argument.type.engType === 'aggregate'
          || argument.type.engType.endsWith('[]');
      }
    }
    if (info.baseCommand) {
      return this.isComplex(argumentName, info.baseCommand);
    } else {
      return false;
    }
  }

  updateEntries(entries: StackedEntry[]) {
    this.renumberHeadings(entries);
    this.entries$.next(entries);
  }

  private renumberHeadings(entries: StackedEntry[]) {
    const counters = [0, 0, 0, 0, 0, 0];
    for (const entry of entries) {
      if (entry instanceof StackedTextEntry) {
        let renderedLines = [];
        for (const line of entry.text.split('\n')) {
          if (line.startsWith('# ')) {
            counters[0]++;
            counters[1] = 0;
            counters[2] = 0;
            counters[3] = 0;
            counters[4] = 0;
            counters[5] = 0;
            const renderedLine = `# ${counters[0]}.&nbsp;&nbsp;` + line.slice(2);
            renderedLines.push(renderedLine);
          } else if (line.startsWith('## ')) {
            counters[1]++;
            counters[2] = 0;
            counters[3] = 0;
            counters[4] = 0;
            counters[5] = 0;
            const renderedLine = `## ${counters[0]}.${counters[1]}.&nbsp;&nbsp;` + line.slice(3);
            renderedLines.push(renderedLine);
          } else if (line.startsWith('### ')) {
            counters[2]++;
            counters[3] = 0;
            counters[4] = 0;
            counters[5] = 0;
            const renderedLine = `### ${counters[0]}.${counters[1]}.${counters[2]}.&nbsp;&nbsp;` + line.slice(4);
            renderedLines.push(renderedLine);
          } else if (line.startsWith('#### ')) {
            counters[3]++;
            counters[4] = 0;
            counters[5] = 0;
            const renderedLine = `#### ${counters[0]}.${counters[1]}.${counters[2]}.${counters[3]}.&nbsp;&nbsp;` + line.slice(5);
            renderedLines.push(renderedLine);
          } else if (line.startsWith('##### ')) {
            counters[4]++;
            counters[5] = 0;
            const renderedLine = `##### ${counters[0]}.${counters[1]}.${counters[2]}.${counters[3]}.${counters[4]}.&nbsp;&nbsp;` + line.slice(6);
            renderedLines.push(renderedLine);
          } else if (line.startsWith('###### ')) {
            counters[5]++;
            const renderedLine = `###### ${counters[0]}.${counters[1]}.${counters[2]}.${counters[3]}.${counters[4]}.${counters[5]}.&nbsp;&nbsp;` + line.slice(7);
            renderedLines.push(renderedLine);
          } else {
            renderedLines.push(line);
          }
        }
        entry.renderedText.set(renderedLines.join('\n'));
      }
    }
  }

  saveStack() {
    const format = utils.getExtension(utils.getFilename(this.objectName))?.toLowerCase();

    const modelEntries = this.entries$.value.map(e => e.model);
    let file;
    switch (format) {
      case 'ycs':
        file = new StackFormatter(modelEntries, {
          advancement: this.advancement,
        }).toJSON();
        break;
      case 'xml':
        file = new StackFormatter(modelEntries, {
          advancement: this.advancement,
        }).toXML();
        break;
      default:
        console.error('Unexpected format');
        return;
    }
    const type = (format === 'xml') ? 'application/xml' : 'application/json';
    const blob = new Blob([file], { type });
    return this.storageClient.uploadObject(this.bucket, this.objectName, blob).then(() => {
      this.dirty$.next(false);
    });
  }
}
