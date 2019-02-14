import { Link } from '@yamcs/client';

export interface LinkItem {
  link: Link;
  hasChildren: boolean;
  expanded: boolean;
  parentLink?: Link;
}
