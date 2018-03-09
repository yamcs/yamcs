import { ParameterSubscriptionRequest } from './types/monitoring';

/**
 * Keeps track of which subscriptions are currently active.
 */
export class SubscriptionModel {

  alarms = false;
  events = false;
  time = false;
  links = false;
  management = false;
  commandQueues = false;
  parameters: ParameterSubscriptionRequest;
}
