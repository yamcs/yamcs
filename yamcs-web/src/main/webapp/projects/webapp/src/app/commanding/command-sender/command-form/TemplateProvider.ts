import { AdvancementParams, CommandOptionType, Value } from '@yamcs/webapp-sdk';

export interface TemplateProvider {
  getAssignment(name: string): Value | undefined;
  getOption(id: string, expectedType: CommandOptionType): Value | undefined;
  getComment(): string | undefined;
  getStream(): string | undefined;
  isDisableTransmissionConstraints(): boolean;
  getAdvancementParams(): AdvancementParams | undefined;
}
