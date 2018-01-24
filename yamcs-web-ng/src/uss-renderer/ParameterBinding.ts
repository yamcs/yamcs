import { AbstractWidget } from './widgets/AbstractWidget';
import { ParameterUpdate } from './ParameterUpdate';

export class ParameterBinding {

  constructor(
    private widget: AbstractWidget,
    private dynamicProperty: string,
    private usingRaw: boolean) {}

  updateWidget(parameterUpdate: ParameterUpdate) {
    switch (this.dynamicProperty) {
      case 'VALUE':
        this.widget.updateValue(parameterUpdate, this.usingRaw);
        break;
      case 'X':
        this.widget.updatePosition(parameterUpdate, 'x', this.usingRaw);
        break;
      case 'Y':
        this.widget.updatePosition(parameterUpdate, 'y', this.usingRaw);
        break;
      case 'FILL_COLOR':
        this.widget.updateFillColor(parameterUpdate, this.usingRaw);
        break;
      default:
        console.warn('Unsupported dynamic property: ' + this.dynamicProperty);
    }
  }
}
