import { Pipe, PipeTransform } from '@angular/core';
import { Parameter, ParameterType } from '../client';

@Pipe({ name: 'parameterTypeForPath' })
export class ParameterTypeForPathPipe implements PipeTransform {

  transform(parameter: Parameter): ParameterType | null | undefined {
    if (!parameter) {
      return null;
    }
    if (!parameter.path) {
      return parameter.type;
    }
    let ptype = parameter.type!;
    for (const segment of parameter.path) {
      if (segment.startsWith('[')) {
        ptype = ptype.arrayInfo!.type;
      } else {
        for (const member of (ptype.member || [])) {
          if (member.name === segment) {
            ptype = member.type as ParameterType;
            break;
          }
        }
      }
    }
    return ptype;
  }
}
