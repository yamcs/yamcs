import { AbstractWidget } from './widgets/AbstractWidget';
import { Parameter } from './Parameter';

export class ParameterBinding {

  constructor(
    private widget: AbstractWidget,
    private dynamicProperty: string,
    private usingRaw: boolean) {}

  updateWidget(para: Parameter) {
    switch (this.dynamicProperty) {
      case 'VALUE':
        this.widget.updateValue(para, this.usingRaw);
        break;
      case 'X':
        this.widget.updatePosition(para, 'x', this.usingRaw);
        break;
      case 'Y':
        this.widget.updatePosition(para, 'y', this.usingRaw);
        break;
      case 'FILL_COLOR':
        this.widget.updateFillColor(para, this.usingRaw);
        break;
      default:
        console.warn('Unsupported dynamic property: ' + this.dynamicProperty);
    }
  }
}
