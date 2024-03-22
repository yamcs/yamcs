import { Pipe, PipeTransform } from '@angular/core';
import { Parameter, ParameterType } from '../client';

@Pipe({ name: 'parameterTypeForPath' })
export class ParameterTypeForPathPipe implements PipeTransform {

  transform(parameter: Parameter, pathString?: string): ParameterType | null | undefined {
    if (!parameter) {
      return null;
    }
    let path = parameter.path;

    // Allow overriding the path (for when it is not contained
    // in the parameter definition)
    if (pathString !== undefined) {
      path = pathString.split('.');
    }

    if (!path) {
      return parameter.type;
    }
    let ptype = parameter.type!;
    for (const segment of path) {
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
