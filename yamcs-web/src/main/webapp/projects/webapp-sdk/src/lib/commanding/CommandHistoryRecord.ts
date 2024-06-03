import { CommandAssignment, CommandHistoryAttribute, CommandHistoryEntry, Value } from '../client';
import { Acknowledgment } from './Acknowledgment';

// Entries that come from a cascading server
// are prefixed with the pattern yamcs_<SERVER>
const CASCADED_PREFIX = /^(yamcs<[^>]+>_)+/;

export class CommandHistoryRecord {

  private entry: CommandHistoryEntry;

  id: string;

  generationTime: string;
  origin: string;
  sequenceNumber: number;

  commandName: string;
  aliases: { [key: string]: string; } = {};

  assignments: CommandAssignment[] = [];
  userAssignments: CommandAssignment[] = [];

  username: string;

  raw: boolean;
  unprocessedBinary: string;
  binary: string;

  comment?: string;
  queue?: string;

  transmissionConstraints?: Acknowledgment;

  queued?: Acknowledgment;
  released?: Acknowledgment;
  sent?: Acknowledgment;
  extraAcks: Acknowledgment[] = [];

  completed?: Acknowledgment;

  extra: { name: string, value: Value; }[] = [];

  acksByName: { [key: string]: Acknowledgment; } = {};

  cascadedRecordsByPrefix = new Map<string, CommandHistoryRecord>();

  constructor(entry: CommandHistoryEntry, prefix?: string) {
    this.entry = entry;
    this.id = entry.id;
    this.generationTime = entry.generationTime;
    this.origin = entry.origin;
    this.sequenceNumber = entry.sequenceNumber;
    this.commandName = entry.commandName;
    this.aliases = entry.aliases || {};

    for (const assignment of (entry.assignments || [])) {
      this.assignments.push(assignment);
      if (assignment.userInput) {
        this.userAssignments.push(assignment);
      }
    }

    const cascadedServers = new Set<string>();

    for (const attr of entry.attr) {
      let attrName = attr.name;
      if (prefix !== undefined) {
        if (!attrName.startsWith(prefix)) {
          continue;
        }
        attrName = attrName.substring(prefix.length);
        if (attrName.startsWith('yamcs<')) { // Multiple cascading, flattened at top
          continue;
        }
      }

      const match = attrName.match(CASCADED_PREFIX);

      if (match) {
        cascadedServers.add(match[0]);
      } else {
        if (attrName === 'username') {
          this.username = attr.value.stringValue!;
        } else if (attrName === 'source') {
          // Legacy, ignore.
        } else if (attrName === 'raw') {
          this.raw = attr.value.booleanValue!;
        } else if (attrName === 'unprocessedBinary') {
          this.unprocessedBinary = attr.value.binaryValue!;
        } else if (attrName === 'binary') {
          this.binary = attr.value.binaryValue!;
        } else if (attrName === 'comment') {
          this.comment = attr.value.stringValue;
        } else if (attrName === 'queue') {
          this.queue = attr.value.stringValue;
        } else if (attrName.endsWith('_Message')) {
          const ackName = attrName.substring(0, attrName.length - '_Message'.length);
          this.saveAckMessage(ackName, attr.value.stringValue!);
        } else if (attrName.endsWith('_Time')) {
          const ackName = attrName.substring(0, attrName.length - '_Time'.length);
          this.saveAckTime(ackName, attr.value.stringValue!);
        } else if (attrName.endsWith('_Status')) {
          const ackName = attrName.substring(0, attrName.length - '_Status'.length);
          this.saveAckStatus(ackName, attr.value.stringValue!);
        } else if (attrName.endsWith('_Return')) {
          const ackName = attrName.substring(0, attrName.length - '_Return'.length);
          this.saveAckReturnValue(ackName, attr.value);
        } else {
          this.extra.push({ name: attrName, value: attr.value });
        }
      }
    }

    for (const ack of Object.values(this.acksByName)) {
      if (ack.name === 'CommandComplete') {
        this.completed = ack;
      } else if (ack.name === 'TransmissionConstraints') {
        this.transmissionConstraints = ack;
      } else if (ack.name === 'Acknowledge_Queued') {
        this.queued = ack;
      } else if (ack.name === 'Acknowledge_Released') {
        this.released = ack;
      } else if (ack.name === 'Acknowledge_Sent') {
        this.sent = ack;
      } else {
        this.extraAcks.push(ack);
      }
    }

    for (const prefix of cascadedServers) {
      this.cascadedRecordsByPrefix.set(prefix, new CommandHistoryRecord(entry, prefix));
    }
  }

  mergeEntry(entry: CommandHistoryEntry, reverse = false): CommandHistoryRecord {

    const mergedAttr = reverse ? this.mergeAttr(entry.attr, this.entry.attr)
      : this.mergeAttr(this.entry.attr, entry.attr);

    const mergedEntry = reverse ? {
      ...this.entry,
      ...entry,
      attr: mergedAttr,
    } : {
      ...entry,
      ...this.entry,
      attr: mergedAttr,
    } as CommandHistoryEntry;

    return new CommandHistoryRecord(mergedEntry);
  }

  private mergeAttr(target: CommandHistoryAttribute[], source: CommandHistoryAttribute[]) {
    const targetKeys = target.map(attr => attr.name);
    const merged = [...target];
    for (const attr of source) {
      const idx = targetKeys.indexOf(attr.name);
      if (idx === -1) {
        merged.push(attr);
      } else {
        merged[idx] = attr;
      }
    }
    return merged;
  }

  private saveAckTime(name: string, time: string) {
    let ack: Acknowledgment | null = null;
    for (const key in this.acksByName) {
      if (key === name) {
        ack = this.acksByName[key];
        break;
      }
    }
    if (!ack) {
      ack = { name };
      this.acksByName[name] = ack;
    }
    ack.time = time;
  }

  private saveAckStatus(name: string, status: string) {
    let ack: Acknowledgment | null = null;
    for (const key in this.acksByName) {
      if (key === name) {
        ack = this.acksByName[key];
        break;
      }
    }
    if (!ack) {
      ack = { name };
      this.acksByName[name] = ack;
    }
    ack.status = status;
  }

  private saveAckMessage(name: string, message: string) {
    let ack: Acknowledgment | null = null;
    for (const key in this.acksByName) {
      if (key === name) {
        ack = this.acksByName[key];
        break;
      }
    }
    if (!ack) {
      ack = { name };
      this.acksByName[name] = ack;
    }
    ack.message = message;
  }

  private saveAckReturnValue(name: string, returnValue: Value) {
    let ack: Acknowledgment | null = null;
    for (const key in this.acksByName) {
      if (key === name) {
        ack = this.acksByName[key];
        break;
      }
    }
    if (!ack) {
      ack = { name };
      this.acksByName[name] = ack;
    }
    ack.returnValue = returnValue;
  }
}
