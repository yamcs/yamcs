import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  standalone: true,
  name: 'polynomial',
})
export class PolynomialPipe implements PipeTransform {

  transform(coefficients?: number[]): string | null {
    if (!coefficients || !coefficients.length) {
      return null;
    }
    let result = '';
    let firstTerm = true;
    for (let i = coefficients.length - 1; i >= 0; i--) {
      if (coefficients[i] !== 0) {
        if (!firstTerm) {
          if (coefficients[i] > 0) {
            result += ' + ';
          } else {
            result += ' - ';
          }
        }
        result += Math.abs(coefficients[i]);
        if (i === 1) {
          result += '𝑥';
        } else if (i >= 1) {
          result += '𝑥<sup>' + i + '</sup>';
        }

        firstTerm = false;
      }
    }
    return result;
  }
}
