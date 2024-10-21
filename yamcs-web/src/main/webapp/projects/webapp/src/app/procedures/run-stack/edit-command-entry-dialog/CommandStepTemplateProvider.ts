import { CommandOptionType, CommandStep, utils, Value } from '@yamcs/webapp-sdk';
import { TemplateProvider } from '../../../commanding/command-sender/command-form/TemplateProvider';

export class CommandStepTemplateProvider implements TemplateProvider {

  constructor(private step: CommandStep) {
  }

  getAssignment(argumentName: string) {
    for (const argName in this.step.args) {
      if (argName === argumentName) {
        const value = this.step.args[argName];
        return utils.toValue(value);
      }
    }
  }

  getOption(id: string, expectedType: CommandOptionType) {
    for (const extraId in (this.step.extra || {})) {
      if (extraId === id) {
        const value = this.step.extra![extraId];
        switch (expectedType) {
          case 'BOOLEAN':
            return this.getBooleanOption(value);
          case 'NUMBER':
            return this.getNumberOption(value);
          case 'STRING':
            return this.getStringOption(value);
          case 'TIMESTAMP':
            return this.getStringOption(value);
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

  getComment() {
    return this.step.comment;
  }

  getStream() {
    return this.step.stream;
  }

  getAdvancementParams() {
    return this.step.advancement;
  }
}
