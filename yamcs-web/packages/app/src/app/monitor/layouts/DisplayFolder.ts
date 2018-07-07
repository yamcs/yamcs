import { ObjectInfo } from '@yamcs/client';

export interface DisplayFolder {
  name: string;
  location: string;

  prefixes: string[];
  objects: ObjectInfo[];
}
