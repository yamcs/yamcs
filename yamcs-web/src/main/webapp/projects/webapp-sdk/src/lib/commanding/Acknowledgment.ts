import { Value } from '../client/types/monitoring';


export interface Acknowledgment {
  name?: string;
  status?: string;
  time?: string;
  message?: string;
  returnValue?: Value;
}
