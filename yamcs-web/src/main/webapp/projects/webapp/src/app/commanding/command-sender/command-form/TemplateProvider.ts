import { AdvancementParams, CommandOptionType, Value } from '@yamcs/webapp-sdk';

export interface TemplateProvider {
  getAssignment(name: string): Value | void;
  getOption(id: string, expectedType: CommandOptionType): Value | void;
  getComment(): string | void;
  getStream(): string | void;
  getAdvancementParams(): AdvancementParams | undefined;
}
