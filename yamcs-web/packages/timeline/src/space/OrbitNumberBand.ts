import EventBand from '../core/EventBand';
import { mergeDeep } from '../utils';

/**
 * Band showing Orbit Numbers.
 */
export default class OrbitNumberBand extends EventBand {

  static get type() {
    return 'OrbitNumberBand';
  }

  static get rules() {
    return mergeDeep({}, EventBand.rules, {
      wrap: false,
      cornerRadius: 0,
      textAlign: 'center',
      borderColor: '#aaa',
      backgroundColor: '#f5f5f5',
      textColor: 'grey',
      borders: 'vertical',
    });
  }
}
