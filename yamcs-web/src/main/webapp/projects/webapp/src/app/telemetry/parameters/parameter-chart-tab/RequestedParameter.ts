import { Parameter } from '@yamcs/webapp-sdk';

/**
 * Describes a requested parameter name, and the
 * matching Parameter definition (which may be missing).
 */
export interface RequestedParameter {
  /**
   * Index in the form array
   */
  index: number;

  traceId: string;

  /**
   * Original requested qualified name
   * (may include aggray offsets)
   */
  requestedName: string;

  /**
   * Definition of the (host) parameter
   */
  parameter?: Parameter;
}
