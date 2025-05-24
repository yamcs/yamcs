package org.yamcs.timeline;

import java.util.UUID;

import org.yamcs.protobuf.StartCondition;

/**
 * Represents a temporal alignment between two items, called the predecessor and the successor.
 * <p>
 * An activity can depend on another activity (= the predecessor), and this class specifies the predecessor's unique
 * identifier and the condition that determines when it should start.
 * <p>
 * The predecessor can be any item (event or activity). The successor must be an activity (because events "just
 * happen").
 */
public record Predecessor(UUID itemId, StartCondition startCondition) {
}
