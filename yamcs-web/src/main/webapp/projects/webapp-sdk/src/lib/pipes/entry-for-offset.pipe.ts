import { Pipe, PipeTransform } from '@angular/core';
import { Member, Parameter, ParameterType } from '../client';

@Pipe({ name: 'entryForOffset' })
export class EntryForOffsetPipe implements PipeTransform {

  transform(parameter: Parameter, offset: string): Parameter | Member | null {
    const entry = parameter.name + offset;
    const parts = entry.split('.');

    let node: Parameter | Member = parameter;
    for (let i = 1; i < parts.length; i++) {
      let memberNode;
      const members: Member[] = this.getParameterTypeForEntry(node)?.member || [];
      for (const member of members) {
        if (member.name === parts[i]) {
          memberNode = member;
          break;
        }
      }

      if (!memberNode) {
        return null;
      } else {
        node = memberNode;
      }
    }

    return node || null;
  }

  private getParameterTypeForEntry(entry: Parameter | Member) {
    const entryType = entry.type as ParameterType;
    if (entryType.arrayInfo) {
      return entryType.arrayInfo.type;
    } else {
      return entry.type;
    }
  }
}
