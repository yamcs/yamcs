import { Value } from '../client';
import { AdvancementParams } from './AdvancementParams';
import { ParameterCheck } from './ParameterCheck';

export type Step = CheckStep | CommandStep | TextStep;

export interface CommandStep {
  type: 'command';
  name: string;
  namespace?: string;
  args: { [key: string]: any; };
  extra?: { [key: string]: Value; };
  advancement?: AdvancementParams;
  stream?: string;
  comment?: string;
}

export interface CheckStep {
  type: 'check';
  parameters: ParameterCheck[];
  comment?: string;
}

export interface TextStep {
  type: 'text';
  text: string;
  comment?: string;
}
