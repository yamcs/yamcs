import { ParameterSubscriptionRequest } from './types/main';

/**
 * Keeps track of which subscriptions are currently active.
 */
export class SubscriptionModel {

  events = false;
  time = false;
  links = false;
  management = false;
  parameters: ParameterSubscriptionRequest;
}
