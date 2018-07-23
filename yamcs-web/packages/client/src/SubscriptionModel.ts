import { ParameterSubscriptionRequest } from './types/monitoring';

/**
 * Keeps track of which subscriptions are currently active.
 */
export class SubscriptionModel {

  alarms = false;
  events = false;
  instance = false;
  time = false;
  links = false;
  management = false;
  processor = false;
  commandQueues = false;
  parameters?: ParameterSubscriptionRequest;
}
