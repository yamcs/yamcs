package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.SpaceSystem;

/**
 * Builds a page result for a collection of matching space systems and other items.
 * <p>
 * Results are sorted in lexicographical order, with space systems on top.
 */
public class MdbPageBuilder<T extends NameDescription> {

    private List<SpaceSystem> spaceSystems;
    private List<T> items;

    private String next;
    private int pos;
    private int limit;

    public MdbPageBuilder(List<SpaceSystem> spaceSystems, List<T> items) {
        this.spaceSystems = spaceSystems;
        this.items = items;

        Collections.sort(spaceSystems, (s1, s2) -> {
            return s1.getQualifiedName().compareToIgnoreCase(s2.getQualifiedName());
        });
        Collections.sort(items, (i1, i2) -> {
            return i1.getQualifiedName().compareToIgnoreCase(i2.getQualifiedName());
        });
    }

    public void setNext(String next) {
        this.next = next;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public MdbPage<T> buildPage() {
        int totalSize = spaceSystems.size() + items.size();
        MdbPage<T> page = new MdbPage<>(totalSize);

        if (next != null) {
            NamedObjectPageToken pageToken = NamedObjectPageToken.decode(next);
            if (pageToken.spaceSystem) {
                for (SpaceSystem spaceSystem : spaceSystems) {
                    if (spaceSystem.getQualifiedName().compareToIgnoreCase(pageToken.name) > 0) {
                        page.addSpaceSystem(spaceSystem);
                    }
                }
                page.addItems(items);
            } else {
                for (T item : items) {
                    if (item.getQualifiedName().compareToIgnoreCase(pageToken.name) > 0) {
                        page.addItem(item);
                    }
                }
            }
        } else if (pos > 0) {
            if (pos < spaceSystems.size()) {
                page.addSpaceSystems(spaceSystems.subList(pos, spaceSystems.size()));
            }
            int itemPos = Math.max(0, pos - spaceSystems.size());
            if (itemPos < items.size()) {
                page.addItems(items.subList(itemPos, items.size()));
            }
        } else {
            page.addSpaceSystems(spaceSystems);
            page.addItems(items);
        }

        page.applyLimit(limit);
        return page;
    }

    public static class MdbPage<T extends NameDescription> {
        private List<SpaceSystem> spaceSystems = new ArrayList<>();
        private List<T> items = new ArrayList<>();
        private int totalSize;
        private String continuationToken;

        MdbPage(int totalSize) {
            this.totalSize = totalSize;
        }

        void addSpaceSystems(List<SpaceSystem> spaceSystems) {
            this.spaceSystems.addAll(spaceSystems);
        }

        void addSpaceSystem(SpaceSystem spaceSystem) {
            spaceSystems.add(spaceSystem);
        }

        void addItems(List<T> items) {
            this.items.addAll(items);
        }

        void addItem(T item) {
            items.add(item);
        }

        public List<SpaceSystem> getSpaceSystems() {
            return spaceSystems;
        }

        public List<T> getItems() {
            return items;
        }

        public int getTotalSize() {
            return totalSize;
        }

        public String getContinuationToken() {
            return continuationToken;
        }

        void applyLimit(int limit) {
            if (limit < spaceSystems.size()) {
                spaceSystems = spaceSystems.subList(0, limit);
                SpaceSystem lastMatch = spaceSystems.get(limit - 1);
                continuationToken = new NamedObjectPageToken(lastMatch.getQualifiedName(), true).encodeAsString();

                items.clear();
                return;
            }

            int itemLimit = limit - spaceSystems.size();
            if (itemLimit < items.size()) {
                items = items.subList(0, itemLimit);
                T lastMatch = items.get(itemLimit - 1);
                continuationToken = new NamedObjectPageToken(lastMatch.getQualifiedName(), false).encodeAsString();
            }
        }
    }
}
