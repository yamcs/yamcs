import { Pipe, PipeTransform } from '@angular/core';
import { Command, TransmissionConstraint } from '../client';
import * as mdb from '../mdb';

/**
 * Combine transmission constraints across the command's hierarchy.
 * Constraints are returned in definition order, from root to leaf,
 * and in order on each node.
 */
@Pipe({
  name: 'allConstraints',
})
export class AllConstraintsPipe implements PipeTransform {
  transform(command: Command | null | undefined): TransmissionConstraint[] {
    return command ? mdb.getAllConstraints(command) : [];
  }
}
