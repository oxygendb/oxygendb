/*
 *
 *
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *
 *
 */
package com.jetbrains.youtrack.db.internal.core.tx;

import com.jetbrains.youtrack.db.internal.common.collection.OMultiCollectionIterator;
import com.jetbrains.youtrack.db.internal.core.db.record.YTIdentifiable;
import com.jetbrains.youtrack.db.internal.core.id.YTRID;
import com.jetbrains.youtrack.db.internal.core.tx.OTransactionIndexChanges.OPERATION;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Collects the changes to an index for a certain key
 */
public class OTransactionIndexChangesPerKey {

  /* internal */ static final int SET_ADD_THRESHOLD = 8;

  public final Object key;
  private final OTxIndexChangesList entries;

  public class OTransactionIndexEntry {

    private final OPERATION operation;
    private YTIdentifiable value;

    public OTransactionIndexEntry(final YTIdentifiable iValue, final OPERATION iOperation) {
      value = iValue;
      operation = iOperation;
    }

    @Override
    public boolean equals(Object obj) {
      // equality intentionally established by the value only, operation is ignored, see
      // interpretAs* methods for details

      if (this == obj) {
        return true;
      }

      if (obj == null || obj.getClass() != OTransactionIndexEntry.class) {
        return false;
      }
      final OTransactionIndexEntry other = (OTransactionIndexEntry) obj;

      if (this.value != null) {
        return this.value.equals(other.value);
      }

      return other.value == null;
    }

    @Override
    public int hashCode() {
      return value == null ? 0 : value.hashCode();
    }

    public OPERATION getOperation() {
      return operation;
    }

    public YTIdentifiable getValue() {
      return value;
    }

    public void setValue(YTIdentifiable newValue) {
      YTRID oldValueId = value == null ? null : value.getIdentity();
      YTRID newValueId = newValue == null ? null : newValue.getIdentity();
      Optional<OTxIndexChangesList.Node> node = entries.getNode(this);

      this.value = newValue;
      node.ifPresent(x -> x.onRidChange(oldValueId, newValueId));
    }
  }

  public OTransactionIndexChangesPerKey(final Object iKey) {
    this.key = iKey;
    entries = new OTxIndexChangesList();
  }

  public void add(YTIdentifiable iValue, final OPERATION operation) {
    synchronized (this) {
      YTRID valueIdentity = iValue == null ? null : iValue.getIdentity();
      if (operation == OPERATION.REMOVE) {
        Optional<OTxIndexChangesList.Node> nodeToRemove =
            entries.getFirstNode(valueIdentity, OPERATION.PUT);
        if (nodeToRemove.isPresent()) {
          nodeToRemove.get().remove();
          return;
        }
      }

      OTransactionIndexEntry item = new OTransactionIndexEntry(valueIdentity, operation);
      entries.add(item);
    }
  }

  /**
   * Interprets this key changes using the given {@link Interpretation interpretation}.
   *
   * @param interpretation the interpretation to use.
   * @return the interpreted changes.
   */
  public Iterable<OTransactionIndexEntry> interpret(Interpretation interpretation) {
    synchronized (this) {
      switch (interpretation) {
        case Unique:
          return interpretAsUnique();
        case Dictionary:
          return interpretAsDictionary();
        case NonUnique:
          return interpretAsNonUnique();
        default:
          throw new IllegalStateException("Unexpected interpretation '" + interpretation + "'");
      }
    }
  }

  public void clear() {
    synchronized (this) {
      this.entries.clear();
    }
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder(64);
    builder.append(key).append(" [");
    boolean first = true;
    for (OTransactionIndexEntry entry : entries) {
      if (first) {
        first = false;
      } else {
        builder.append(',');
      }

      builder.append(entry.value).append(" (").append(entry.operation).append(")");
    }
    builder.append("]");
    return builder.toString();
  }

  private Iterable<OTransactionIndexEntry> interpretAsUnique() {
    // 1. Handle common fast paths.

    if (entries.size() < 2) {
      return new ArrayList<OTransactionIndexEntry>(entries);
    }

    if (entries.size() == 2) {
      final OTransactionIndexEntry entryA = entries.get(0);
      final OTransactionIndexEntry entryB = entries.get(1);

      if (entryA.operation == OPERATION.REMOVE && entryB.operation == OPERATION.REMOVE) {
        return Collections.singletonList(entryA); // only one removal is observed anyway
      }

      final YTRID ridA = entryA.value == null ? null : entryA.value.getIdentity();
      final YTRID ridB = entryB.value == null ? null : entryB.value.getIdentity();

      if (ridA != null && ridA.equals(ridB)) {
        if (entryA.operation == entryB.operation) // both operations do the same on the same RID
        {
          return Collections.singletonList(entryA);
        }

        return new ArrayList<OTransactionIndexEntry>(
            entries); // don't optimize remove-put on the same RID for safety
      }

      /* latest key removal wins */
      /* reorder to remove-put */
      if (entryB.operation == OPERATION.REMOVE) {
        if (entryB.value == null) {
          return Collections.singletonList(entryB);
        } else {
          return swap(entries);
        }
      }

      return new ArrayList<OTransactionIndexEntry>(entries); // it's either remove-put or put-put
    }

    // 2. Calculate observable changes to index.

    final Set<OTransactionIndexEntry> interpretation =
        new HashSet<OTransactionIndexEntry>(entries.size());
    OTransactionIndexEntry firstExternalRemove = null;
    for (OTransactionIndexEntry entry : entries) {
      final YTIdentifiable value = entry.value;

      switch (entry.operation) {
        case PUT:
          assert value != null;
          interpretation.add(entry);
          break;
        case REMOVE:
          if (!interpretation.remove(entry)) {
            if (firstExternalRemove == null) {
              firstExternalRemove =
                  entry; // record only first external removal, it makes key ready for any put
            }
            // anyway
            if (value == null) {
              interpretation.clear();
            }
          }
          break;
        case CLEAR:
        default:
          assert false;
          break;
      }
    }

    // 3. Build resulting equivalent operation sequence.

    if (interpretation.isEmpty()) { // no observable changes except maybe some removal
      if (firstExternalRemove != null) {
        return Collections.singletonList(firstExternalRemove);
      }

      return Collections.emptyList();
    }

    final List<OTransactionIndexEntry> changes =
        new ArrayList<OTransactionIndexEntry>(1 /* for removal, if any */ + 2 /* for puts */);
    if (firstExternalRemove != null) {
      changes.add(firstExternalRemove);
    }

    int counter = 0;
    for (OTransactionIndexEntry entry : interpretation) {
      changes.add(entry);

      if (++counter == 2) {
        break; // unique constraint already violated, stop
      }
    }

    return changes;
  }

  private Iterable<OTransactionIndexEntry> interpretAsDictionary() {
    // 1. Handle common fast paths.

    if (entries.size() < 2) {
      return new ArrayList<OTransactionIndexEntry>(entries);
    }

    if (entries.size() == 2) {
      final OTransactionIndexEntry entryA = entries.get(0);
      final OTransactionIndexEntry entryB = entries.get(1);

      if (entryA.operation == OPERATION.REMOVE && entryB.operation == OPERATION.REMOVE) {
        return Collections.singletonList(entryA); // only one removal is observed anyway
      }

      final YTRID ridA = entryA.value == null ? null : entryA.value.getIdentity();
      final YTRID ridB = entryB.value == null ? null : entryB.value.getIdentity();

      if (ridA != null && ridA.equals(ridB)) {
        if (entryA.operation == entryB.operation) // both operations do the same on the same RID
        {
          return Collections.singletonList(entryA);
        }

        return new ArrayList<OTransactionIndexEntry>(entries);
      }

      if (entryB.operation == OPERATION.REMOVE && entryB.value == null) {
        return Collections.singletonList(entryB); // latest key removal wins
      }

      /* latest put wins */
      /* it's put-remove on different RIDs, put wins */
      if (entryB.operation == OPERATION.PUT) {
        return Collections.singletonList(entryB);
      } else {
        return Collections.singletonList(entryA);
      }
    }

    // 2. Calculate observable changes to index.

    // XXX: We need to return only *latest observable* put, it always wins to other puts and
    // removals. Unfortunately, there
    // is no lightweight way to find it out using standard Java data structures, thanks to Josh
    // Bloch for not exposing the
    // LinkedHashMap's "doubly-linked list" interface to the public, but mentioning it in the clever
    // javadoc. So we have to
    // maintain our own queue.
    final Deque<OTransactionIndexEntry> lastObservedPuts =
        new ArrayDeque<OTransactionIndexEntry>(entries.size());

    final Set<OTransactionIndexEntry> interpretation =
        new HashSet<OTransactionIndexEntry>(entries.size());
    OTransactionIndexEntry firstExternalRemove = null;
    for (OTransactionIndexEntry entry : entries) {
      final YTIdentifiable value = entry.value;

      switch (entry.operation) {
        case PUT:
          assert value != null;

          interpretation.add(entry);
          lastObservedPuts.addLast(entry);
          break;
        case REMOVE:
          if (interpretation.remove(entry)) { // the put of this RID is no longer observable
            assert value != null;

            // Recalculate last visible put.

            if (entry.equals(lastObservedPuts.peekLast())) {
              lastObservedPuts.removeLast();
            }

            OTransactionIndexEntry last;
            while ((last = lastObservedPuts.peekLast()) != null
                && !interpretation.contains(last)) // prune all unobservable puts
            {
              lastObservedPuts.removeLast();
            }
          } else {
            if (firstExternalRemove == null) // save only first external remove
            {
              firstExternalRemove = entry;
            }
            if (value == null) { // start from the scratch
              interpretation.clear();
              lastObservedPuts.clear();
            }
          }
          break;

        case CLEAR:
        default:
          assert false;
          break;
      }
    }

    // 3. Build resulting equivalent operation sequence.

    if (interpretation.isEmpty()) { // no observable changes except maybe some removal
      if (firstExternalRemove != null) {
        return Collections.singletonList(firstExternalRemove);
      }

      return Collections.emptyList();
    }

    return Collections.singletonList(lastObservedPuts.getLast()); // last visible put
  }

  private Iterable<OTransactionIndexEntry> interpretAsNonUnique() {
    // 1. Handle common fast paths.

    if (entries.size() < 2) {
      return new ArrayList<OTransactionIndexEntry>(entries);
    }

    if (entries.size() == 2) {
      final OTransactionIndexEntry entryA = entries.get(0);
      final OTransactionIndexEntry entryB = entries.get(1);

      final YTRID ridA = entryA.value == null ? null : entryA.value.getIdentity();
      final YTRID ridB = entryB.value == null ? null : entryB.value.getIdentity();

      if (ridA == null) {
        assert entryA.operation == OPERATION.REMOVE;

        if (entryB.operation == OPERATION.REMOVE) {
          return Collections.singletonList(entryA); // both are removals
        }

        return new ArrayList<OTransactionIndexEntry>(entries); // second operation is a put
      }

      if (ridB == null) {
        assert entryB.operation == OPERATION.REMOVE;

        return Collections.singletonList(entryB); // the only observable change is a key removal
      }

      if (ridA.equals(ridB)) {
        if (entryA.operation == entryB.operation) // both operations do the same on the same RID
        {
          return Collections.singletonList(entryA);
        }

        return new ArrayList<OTransactionIndexEntry>(entries);
      }

      return new ArrayList<OTransactionIndexEntry>(entries); // it's put-put on different RIDs
    }

    // 2. Calculate observable changes to index.

    final Set<OTransactionIndexEntry> changes = new HashSet<OTransactionIndexEntry>(entries.size());

    final Set<OTransactionIndexEntry> interpretation =
        new HashSet<OTransactionIndexEntry>(entries.size());
    boolean seenKeyRemoval = false;
    for (OTransactionIndexEntry entry : entries) {
      final YTIdentifiable value = entry.value;
      final YTRID rid = value == null ? null : value.getIdentity();

      switch (entry.operation) {
        case PUT:
          assert rid != null;

          interpretation.add(entry);
          break;
        case REMOVE:
          if (rid == null) {
            interpretation.clear();

            changes.clear();
            changes.add(entry); // save key removal as it affects the key regardless of the RID
            seenKeyRemoval = true;
          } else {
            if (!interpretation.remove(entry)) {
              if (!seenKeyRemoval) // no point in removing a RID from the removed key
              {
                changes.add(
                    entry); // save that operation to remove the RID potentially created outside of
              }
            }
            // a transaction
          }
          break;

        case CLEAR:
        default:
          assert false;
          break;
      }
    }

    // 3. Build resulting equivalent operation sequence.

    changes.removeAll(
        interpretation); // remove any removal which has a corresponding put, put is enough

    if (interpretation.isEmpty()) // no observable changes except maybe some removals
    {
      return changes; // it's either single key removal or one or more RID removals
    }

    if (!seenKeyRemoval /* since key removal is an ordered operation */
        && interpretation.size() < SET_ADD_THRESHOLD) {
      changes.addAll(interpretation);
      return changes;
    } else {
      final OMultiCollectionIterator<OTransactionIndexEntry> result =
          new OMultiCollectionIterator<OTransactionIndexEntry>();
      result.setAutoConvertToRecord(false);
      result.add(changes);
      result.add(interpretation);
      return result;
    }
  }

  private static Iterable<OTransactionIndexEntry> swap(List<OTransactionIndexEntry> list) {
    assert list.size() == 2;
    final List<OTransactionIndexEntry> result = new ArrayList<OTransactionIndexEntry>(2);
    result.add(list.get(1));
    result.add(list.get(0));
    return result;
  }

  /**
   * Defines interpretations supported by {@link #interpret(Interpretation)}.
   */
  public enum Interpretation {
    /**
     * Interpret changes like they was done for unique index.
     */
    Unique,

    /**
     * Interpret changes like they was done for dictionary index.
     */
    Dictionary,

    /**
     * Interpret changes like they was done for non-unique index.
     */
    NonUnique
  }

  public boolean isEmpty() {
    return entries == null || entries.isEmpty();
  }

  public int size() {
    return entries == null ? 0 : entries.size();
  }

  /**
   * @return a copy of the entries of this object
   */
  public List<OTransactionIndexEntry> getEntriesAsList() {
    return Collections.unmodifiableList(new ArrayList<>(this.entries));
  }

  /**
   * Only needed for old tests, will be removed soon. PLEASE DON'T USE IT
   *
   * @return the entries (not a copy, the exact list)
   */
  protected List<OTransactionIndexEntry> getEntriesInternal() {
    return entries;
  }

  /**
   * Only needed for old tests, will be removed soon. PLEASE DON'T USE IT
   *
   * @param iValue
   * @param iOperation
   * @return
   */
  protected OTransactionIndexEntry createEntryInternal(
      final YTIdentifiable iValue, final OPERATION iOperation) {
    return new OTransactionIndexEntry(iValue, iOperation);
  }
}