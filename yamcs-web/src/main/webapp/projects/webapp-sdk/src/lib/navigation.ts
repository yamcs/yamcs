import { User } from './User';

export type NavGroup =
  | 'telemetry'
  | 'commanding'
  | 'procedures' // Deprecated (renamed to "automation")
  | 'automation'
  | 'archive'
  | 'mdb';

export interface NavItem {
  path: string;
  label: string;
  activeWhen?: string;
  icon?: string;
  condition?: (user: User) => boolean;

  /**
   * Optional hint to order items in sidebar. Defaults to 0.
   *
   * Only used for extension paths.
   */
  order?: number;
}
