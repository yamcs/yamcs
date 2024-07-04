export interface Spec {
  options: Option[];
}

export type OptionType = 'BOOLEAN' | 'FLOAT' | 'STRING' | 'INTEGER';

export interface Option {
  name: string;
  type: OptionType;
  default: any;
  hidden: boolean;
  required: boolean;
  secret: boolean;
  description?: string[];
}
