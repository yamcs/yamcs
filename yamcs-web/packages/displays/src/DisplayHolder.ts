import { OpenDisplayCommandOptions } from './OpenDisplayCommandOptions';

/**
 * Holder for a display. For example: a frame in a layout is a display holder.
 */
export interface DisplayHolder {

  /**
   * Returns the base id for relative links
   * (used by widgets that have navigation capabilities)
   */
  getBaseId(): string;

  /**
   * Requestd to open a display
   * (used by widgets that have navigation capabilities)
   */
  openDisplay(options: OpenDisplayCommandOptions): void;

  /**
   * Request to close a display
   * (used by widgets that have navigation capabilities)
   */
  closeDisplay(): void;
}
