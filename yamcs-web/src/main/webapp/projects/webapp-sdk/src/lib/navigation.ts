import { User } from './User';

export type NavGroup = 'telemetry' | 'commanding' | 'archive' | 'mdb';

export interface NavItem {
  path: string;
  label: string;
  icon?: string;
  condition?: (user: User) => boolean;
}
