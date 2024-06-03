import { Pipe, PipeTransform } from '@angular/core';
import { Parameter, ParameterType } from '../client';
import { getParameterTypeForPath } from '../utils';

@Pipe({
  standalone: true,
  name: 'parameterTypeForPath',
})
export class ParameterTypeForPathPipe implements PipeTransform {

  transform(parameter: Parameter, pathString?: string): ParameterType | null | undefined {
    return getParameterTypeForPath(parameter, pathString);
  }
}
