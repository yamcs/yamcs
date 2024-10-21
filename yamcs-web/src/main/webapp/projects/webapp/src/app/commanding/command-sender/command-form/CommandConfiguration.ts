import { AdvancementParams } from '@yamcs/webapp-sdk';

export interface CommandConfiguration {

  args: { [key: string]: any; };
  extra: { [key: string]: any; };
  stream?: string;
  comment?: string;
  advancement?: AdvancementParams;
}
