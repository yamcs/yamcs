import { Command, TransmissionConstraint } from './client';

/**
 * Combine transmission constraints across the command's hierarchy.
 * Constraints are returned in definition order, from root to leaf,
 * and in order on each node.
 */
export function getAllConstraints(command: Command) {
  const defs: Command[] = [];

  let node: Command | null = command;
  while (node) {
    defs.push(node);
    node = node.baseCommand ?? null;
  }

  const constraints: TransmissionConstraint[] = [];

  // Iterate from root to leaf
  for (const def of defs.reverse()) {
    if (def.constraint) {
      constraints.push(...def.constraint);
    }
  }

  return constraints;
}
