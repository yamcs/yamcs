export interface Spec {
  options: Option[];
}

export type OptionType = 'STRING' | 'INTEGER';

export interface Option {
  name: string;
  type: OptionType;
  default: any;
  hidden: boolean;
  required: boolean;
  secret: boolean;
}
