import { Link } from '@yamcs/webapp-sdk';

export interface LinkItem {
  link: Link;
  hasChildren: boolean;
  expanded: boolean;
  parentLink?: Link;
}
