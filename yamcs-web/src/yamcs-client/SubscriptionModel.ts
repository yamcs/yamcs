import { ParameterSubscriptionRequest } from './types/main';

/**
 * Keeps track of which subscriptions are currently active.
 */
export class SubscriptionModel {

  time = false;
  links = false;
  management = false;
  parameters: ParameterSubscriptionRequest;
}
