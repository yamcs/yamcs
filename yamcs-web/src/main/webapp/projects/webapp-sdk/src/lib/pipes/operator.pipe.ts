import { Pipe, PipeTransform } from '@angular/core';
import { OperatorType } from '../client';

@Pipe({
  standalone: true,
  name: 'operator',
})
export class OperatorPipe implements PipeTransform {

  transform(operator?: OperatorType): string | null {
    if (!operator) {
      return null;
    }
    switch (operator) {
      case 'EQUAL_TO':
        return '==';
      case 'NOT_EQUAL_TO':
        return '!=';
      case 'GREATER_THAN':
        return '>';
      case 'GREATER_THAN_OR_EQUAL_TO':
        return '>=';
      case 'SMALLER_THAN':
        return '<';
      case 'SMALLER_THAN_OR_EQUAL_TO':
        return '<=';
      default:
        return operator;
    }
  }
}
