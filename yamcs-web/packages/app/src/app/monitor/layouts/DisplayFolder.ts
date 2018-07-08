import { ObjectInfo } from '@yamcs/client';

export interface DisplayFolder {
  location: string;

  prefixes: string[];
  objects: ObjectInfo[];
}
