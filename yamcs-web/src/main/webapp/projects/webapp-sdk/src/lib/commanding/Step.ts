import { Value } from '../client';
import { AdvancementParams } from './AdvancementParams';
import { ParameterCheck } from './ParameterCheck';
import { VerifyComparison } from './VerifyComparison';

export type Step = CheckStep | CommandStep | TextStep | VerifyStep;

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

export interface VerifyStep {
  type: 'verify';
  condition: VerifyComparison[];
  delay?: number;
  timeout?: number;
  comment?: string;
}
