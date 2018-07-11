import { OpenDisplayCommandOptions } from './OpenDisplayCommandOptions';

/**
 * Defines what should happen when navigation occurs from within a display. Usually via a navigation button.
 */
export interface NavigationHandler {

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
