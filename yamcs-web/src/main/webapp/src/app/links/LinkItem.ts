import { Link } from '../client';

export interface LinkItem {
  link: Link;
  hasChildren: boolean;
  expanded: boolean;
  parentLink?: Link;
}
