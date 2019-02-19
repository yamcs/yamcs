import { ParameterSubscriptionRequest } from './types/monitoring';

/**
 * Keeps track of which subscriptions are currently active.
 */
export class SubscriptionModel {

  alarms = false;
  commandQueues = false;
  events = false;
  instance = false;
  time = false;
  links = false;
  management = false;
  processor = false;
  stream = false;

  parameters?: ParameterSubscriptionRequest;
  streamName?: string;
}
