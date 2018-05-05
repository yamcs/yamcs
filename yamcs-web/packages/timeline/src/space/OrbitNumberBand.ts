import Timeline from '../Timeline';
import EventBand, { EventBandOptions, EventBandStyle } from '../core/EventBand';
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
      cornerRadius: 0,
      textAlign: 'center',
      borderColor: '#aaa',
      backgroundColor: '#f5f5f5',
      textColor: 'grey',
      borders: 'vertical',
    });
  }

  constructor(timeline: Timeline, protected opts: EventBandOptions, protected style: EventBandStyle) {
    super(timeline, { ...opts, wrap: false }, style);
  }
}
