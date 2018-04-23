import { InjectionToken } from '@angular/core';
import { ColumnInfo } from '../../shared/template/ColumnChooser';

export interface SidebarItem {
  routerLink: string;
  label: string;
}

export interface ExtraColumnInfo extends ColumnInfo {
  /**
   * id of another column after which to insert this column.
   * This only impacts the ordering in the column chooser dropdown.
   *
   * Typically you want to set this so that the ordering matches
   * the configured ordering of 'displayedColumns'.
   */
  after: string;
}

export interface AppConfig {
  monitor?: {
    extraItems?: SidebarItem[];
  };
  events?: {
    extraColumns?: ExtraColumnInfo[];
    displayedColumns?: string[];
  };
}

/**
 * Token for injecting app-specific config into components
 */
export const APP_CONFIG = new InjectionToken<AppConfig>('app.config');
