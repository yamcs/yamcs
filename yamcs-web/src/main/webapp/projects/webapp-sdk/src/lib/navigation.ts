import { User } from './User';

export type NavGroup = 'telemetry' | 'commanding' | 'procedures' | 'archive' | 'mdb';

export interface NavItem {
  path: string;
  label: string;
  icon?: string;
  condition?: (user: User) => boolean;
}
