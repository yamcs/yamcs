import { CommandHistoryEntry } from '@yamcs/client';
import { CommandHistoryEvent } from './CommandHistoryEvent';

export class CommandHistoryRecord {

  generationTime: string;
  origin: string;
  sequenceNumber: number;

  username: string;

  source: string;
  binary: string;

  comment?: string;

  sentEvent?: CommandHistoryEvent;
  verifierEvents: CommandHistoryEvent[] = [];

  completed = false;
  success?: boolean;
  failureMessage?: string;

  constructor(entry: CommandHistoryEntry) {
    this.generationTime = entry.generationTimeUTC;
    this.origin = entry.commandId.origin;
    this.sequenceNumber = entry.commandId.sequenceNumber;

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
      } else if (attr.name === 'Comment') {
        this.comment = attr.value.stringValue;
      } else if (attr.name === 'Acknowledge_Sent_Status') {
        if (!this.sentEvent) {
          this.sentEvent = { name: 'Acknowledge_Sent' };
        }
        this.sentEvent.status = attr.value.stringValue;
      } else if (attr.name === 'Acknowledge_Sent_Time') {
        if (!this.sentEvent) {
          this.sentEvent = { name: 'Acknowledge_Sent' };
        }
        this.sentEvent.time = attr.value.stringValue;
      } else if (attr.name.indexOf('Verifier_') === 0) {
        const match = attr.name.match(/Verifier_(.*)_(Time|Status)/);
        if (match) {
          this.updateVerifierEvent(match[1], match[2], attr.value.stringValue!);
        }
      }
    }
  }

  private updateVerifierEvent(stage: string, attributeName: string, value: string) {
    let event;
    for (const existingEvent of this.verifierEvents) {
      if (existingEvent.name === stage) {
        event = existingEvent;
        break;
      }
    }
    if (!event) {
      event = { name: stage };
      this.verifierEvents.push(event);
    }
    if (attributeName === 'Time') {
      event.time = value;
    } else if (attributeName === 'Status') {
      event.status = value;
    }
  }
}
