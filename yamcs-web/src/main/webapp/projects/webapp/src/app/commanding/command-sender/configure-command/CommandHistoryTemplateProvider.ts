import { AdvancementParams, CommandHistoryEntry, CommandOptionType, Value } from '@yamcs/webapp-sdk';
import { TemplateProvider } from '../command-form/TemplateProvider';

export class CommandHistoryTemplateProvider implements TemplateProvider {

  constructor(private entry: CommandHistoryEntry) {
  }

  getAssignment(argumentName: string) {
    if (this.entry.assignments) {
      for (const assignment of this.entry.assignments) {
        if (assignment.name === argumentName) {
          return assignment.value;
        }
      }
    }
  }

  getOption(id: string, expectedType: CommandOptionType) {
    for (const attr of (this.entry.attr || [])) {
      if (attr.name === id) {
        switch (expectedType) {
          case 'BOOLEAN':
            return this.getBooleanOption(attr.value);
          case 'NUMBER':
            return this.getNumberOption(attr.value);
          case 'STRING':
            return this.getStringOption(attr.value);
          case 'TIMESTAMP':
            return this.getTimestampOption(attr.value);
        }
      }
    }
  }

  private getBooleanOption(value: Value) {
    if (value.type === 'BOOLEAN') {
      return value;
    }
  }

  private getNumberOption(value: Value) {
    switch (value.type) {
      case 'SINT32':
      case 'UINT32':
      case 'SINT64':
      case 'UINT64':
        return value;
    }
  }

  private getStringOption(value: Value) {
    if (value.type === 'STRING') {
      return value;
    }
  }

  private getTimestampOption(value: Value) {
    if (value.type === 'TIMESTAMP') {
      return value;
    }
  }

  getComment() {
    // Don't copy
  }

  getStream() {
    // Don't copy (not currently stored)
  }

  getAdvancementParams(): AdvancementParams | undefined {
    return undefined;
  }
}
