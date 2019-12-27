import { ObjectInfo } from '../../client';

export interface DisplayFolder {
  location: string;

  prefixes: string[];
  objects: ObjectInfo[];
}
