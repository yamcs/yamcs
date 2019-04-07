import { CommandHistoryEntry } from '@yamcs/client';
import * as utils from '../../shared/utils';
import { CommandHistoryStage } from './CommandHistoryStage';

export class CommandHistoryRecord {

  generationTime: string;
  origin: string;
  sequenceNumber: number;

  commandName: string;

  username: string;

  source: string;
  binary: string;

  comment?: string;

  stages: CommandHistoryStage[] = [];

  extra: { [key: string]: string }[] = [];

  completed = false;
  success?: boolean;
  failureMessage?: string;

  constructor(entry: CommandHistoryEntry) {
    this.generationTime = entry.generationTimeUTC;
    this.origin = entry.commandId.origin;
    this.sequenceNumber = entry.commandId.sequenceNumber;
    this.commandName = entry.commandId.commandName;

    for (const attr of entry.attr) {
      if (attr.name === 'username') {
        this.username = attr.value.stringValue!;
      } else if (attr.name === 'source') {
        this.source = attr.value.stringValue!;
      } else if (attr.name === 'binary') {
        this.binary = attr.value.binaryValue!;
      } else if (attr.name === 'CommandFailed') {
        this.failureMessage = attr.value.stringValue!;
      } else if (attr.name === 'CommandComplete') {
        this.completed = true;
        this.success = attr.value.stringValue === 'OK';
      } else if (attr.name === 'Comment' || attr.name === 'comment') { // Old versions of Yamcs use "Comment" with capital
        this.comment = attr.value.stringValue;
      } else if (attr.name.indexOf('Verifier_') === 0) {
        const match = attr.name.match(/Verifier_(.*)_(Time|Status)/);
        if (match) {
          this.updateStageEvent(match[1], match[2], attr.value.stringValue!);
        }
      } else if (attr.name.indexOf('Acknowledge_') === 0) {
        const match = attr.name.match(/Acknowledge_(.*)_(Time|Status)/);
        if (match) {
          this.updateStageEvent(match[1], match[2], attr.value.stringValue!);
        }
      } else if (attr.name === 'TransmissionConstraints') {
        // TODO
      } else {
        this.extra.push({ name: attr.name, value: utils.printValue(attr.value) });
      }
    }
  }

  private updateStageEvent(stage: string, attributeName: string, value: string) {
    let event;
    for (const existingEvent of this.stages) {
      if (existingEvent.name === stage) {
        event = existingEvent;
        break;
      }
    }
    if (!event) {
      event = { name: stage };
      this.stages.push(event);
    }
    if (attributeName === 'Time') {
      event.time = value;
    } else if (attributeName === 'Status') {
      event.status = value;
    }
  }
}
