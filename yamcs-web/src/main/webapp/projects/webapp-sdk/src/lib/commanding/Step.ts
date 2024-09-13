import { Value } from '../client';
import { AdvancementParams } from './AdvancementParams';
import { ParameterCheck } from './ParameterCheck';

export type Step = CheckStep | CommandStep;

export interface CommandStep {
  type: 'command',
  name: string;
  namespace?: string;
  args: { [key: string]: any; };
  extra?: { [key: string]: Value; };
  advancement?: AdvancementParams;
  comment?: string;
}

export interface CheckStep {
  type: 'check',
  parameters: ParameterCheck[];
  comment?: string;
}
