export type TrackerMode = 'location' | 'event' | 'all';

export type PanMode = 'XY' | 'X_ONLY' | 'Y_ONLY' | 'NONE';

export interface TimelineOptions {

  theme?: string;
  zoom?: number;
  initialDate?: Date | string;
  style?: any;
  tracker?: any;
  nodata?: boolean;
  wallclock?: boolean;
  domReduction?: boolean;
  data?: any;
  pannable?: boolean | PanMode;
  sidebarWidth?: number;
}
