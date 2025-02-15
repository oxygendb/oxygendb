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
package com.jetbrains.youtrack.db.internal.core.fetch;

import com.jetbrains.youtrack.db.api.exception.RecordNotFoundException;
import com.jetbrains.youtrack.db.api.record.DBRecord;
import com.jetbrains.youtrack.db.api.record.Identifiable;
import com.jetbrains.youtrack.db.api.record.RID;
import com.jetbrains.youtrack.db.api.schema.PropertyType;
import com.jetbrains.youtrack.db.internal.common.collection.MultiCollectionIterator;
import com.jetbrains.youtrack.db.internal.common.collection.MultiValue;
import com.jetbrains.youtrack.db.internal.common.log.LogManager;
import com.jetbrains.youtrack.db.internal.core.db.record.ridbag.RidBag;
import com.jetbrains.youtrack.db.internal.core.fetch.json.JSONFetchContext;
import com.jetbrains.youtrack.db.internal.core.id.RecordId;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityInternalUtils;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.StringSerializerHelper;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.record.string.RecordSerializerJSON;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class for fetching.
 */
public class FetchHelper {

  public static final String DEFAULT = "*:0";
  public static final FetchPlan DEFAULT_FETCHPLAN = new FetchPlan(DEFAULT);

  public static FetchPlan buildFetchPlan(final String iFetchPlan) {
    if (iFetchPlan == null) {
      return null;
    }

    if (DEFAULT.equals(iFetchPlan)) {
      return DEFAULT_FETCHPLAN;
    }

    return new FetchPlan(iFetchPlan);
  }

  public static void fetch(
      final DBRecord rootRecord,
      final Object userObject,
      final FetchPlan fetchPlan,
      final FetchListener listener,
      final FetchContext context,
      final String format) {
    try {
      if (rootRecord instanceof EntityImpl record) {
        // SCHEMA AWARE
        final Object2IntOpenHashMap<RID> parsedRecords = new Object2IntOpenHashMap<>();
        parsedRecords.defaultReturnValue(-1);

        final boolean isEmbedded = record.isEmbedded() || !record.getIdentity().isPersistent();
        if (!isEmbedded) {
          parsedRecords.put(rootRecord.getIdentity(), 0);
        }

        if (!format.contains("shallow")) {
          processRecordRidMap(record, fetchPlan, 0, 0, -1, parsedRecords, "", context);
        }
        processRecord(
            record, userObject, fetchPlan, 0, 0, -1, parsedRecords, "", listener, context, format);
      }
    } catch (final Exception e) {
      LogManager.instance()
          .error(FetchHelper.class, "Fetching error on record %s", e, rootRecord.getIdentity());
    }
  }

  public static void checkFetchPlanValid(final String iFetchPlan) {

    if (iFetchPlan != null && !iFetchPlan.isEmpty()) {
      // CHECK IF THERE IS SOME FETCH-DEPTH
      final List<String> planParts = StringSerializerHelper.split(iFetchPlan, ' ');
      if (!planParts.isEmpty()) {
        for (String planPart : planParts) {
          final List<String> parts = StringSerializerHelper.split(planPart, ':');
          if (parts.size() != 2) {
            throw new IllegalArgumentException("Fetch plan '" + iFetchPlan + "' is invalid");
          }
        }
      } else {
        throw new IllegalArgumentException("Fetch plan '" + iFetchPlan + "' is invalid");
      }
    }
  }

  private static int getDepthLevel(
      final FetchPlan iFetchPlan, final String iFieldPath, final int iCurrentLevel) {
    if (iFetchPlan == null) {
      return 0;
    }
    return iFetchPlan.getDepthLevel(iFieldPath, iCurrentLevel);
  }

  public static void processRecordRidMap(
      final EntityImpl record,
      final FetchPlan iFetchPlan,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchContext iContext) {
    if (iFetchPlan == null) {
      return;
    }

    if (iFetchPlan == FetchHelper.DEFAULT_FETCHPLAN) {
      return;
    }

    Object fieldValue;
    for (String fieldName : record.getPropertyNamesInternal()) {
      int depthLevel;
      final String fieldPath =
          !iFieldPathFromRoot.isEmpty() ? iFieldPathFromRoot + "." + fieldName : fieldName;

      depthLevel = getDepthLevel(iFetchPlan, fieldPath, iCurrentLevel);
      if (depthLevel == -2) {
        continue;
      }
      if (iFieldDepthLevel > -1) {
        depthLevel = iFieldDepthLevel;
      }

      fieldValue = EntityInternalUtils.getRawProperty(record, fieldName);
      if (fieldValue == null
          || !(fieldValue instanceof Identifiable)
          && (!(fieldValue instanceof Iterable<?>)
          || !((Iterable<?>) fieldValue).iterator().hasNext()
          || ((Iterable<?>) fieldValue).iterator().next() == null)
          && (!(fieldValue instanceof Collection<?>)
          || ((Collection<?>) fieldValue).isEmpty()
          || !(((Collection<?>) fieldValue).iterator().next() instanceof Identifiable))
          && (!(fieldValue.getClass().isArray())
          || Array.getLength(fieldValue) == 0
          || !(Array.get(fieldValue, 0) instanceof Identifiable))
          && (!(fieldValue instanceof MultiCollectionIterator<?>))
          && (!(fieldValue instanceof Map<?, ?>)
          || ((Map<?, ?>) fieldValue).isEmpty()
          || !(((Map<?, ?>) fieldValue).values().iterator().next()
          instanceof Identifiable))) {
        //noinspection UnnecessaryContinue
        continue;
      } else {
        try {
          final boolean isEmbedded = isEmbedded(fieldValue);
          if (!(isEmbedded && iContext.fetchEmbeddedDocuments())
              && !iFetchPlan.has(fieldPath, iCurrentLevel)
              && depthLevel > -1
              && iCurrentLevel >= depthLevel)
          // MAX DEPTH REACHED: STOP TO FETCH THIS FIELD
          {
            continue;
          }

          final int nextLevel = isEmbedded ? iLevelFromRoot : iLevelFromRoot + 1;

          if (fieldValue instanceof RecordId) {
            fieldValue = ((RecordId) fieldValue).getRecord();
          }

          fetchRidMap(
              iFetchPlan,
              fieldValue,
              iCurrentLevel,
              nextLevel,
              iFieldDepthLevel,
              parsedRecords,
              fieldPath,
              iContext);
        } catch (Exception e) {
          LogManager.instance()
              .error(FetchHelper.class, "Fetching error on record %s", e, record.getIdentity());
        }
      }
    }
  }

  private static void fetchRidMap(
      final FetchPlan iFetchPlan,
      final Object fieldValue,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchContext iContext) {
    if (fieldValue == null) {
      //noinspection UnnecessaryReturnStatement
      return;
    } else if (fieldValue instanceof EntityImpl) {
      fetchDocumentRidMap(
          iFetchPlan,
          fieldValue,
          iCurrentLevel,
          iLevelFromRoot,
          iFieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iContext);
    } else if (fieldValue instanceof Iterable<?>) {
      fetchCollectionRidMap(
          iFetchPlan,
          fieldValue,
          iCurrentLevel,
          iLevelFromRoot,
          iFieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iContext);
    } else if (fieldValue.getClass().isArray()) {
      fetchArrayRidMap(
          iFetchPlan,
          fieldValue,
          iCurrentLevel,
          iLevelFromRoot,
          iFieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iContext);
    } else if (fieldValue instanceof Map<?, ?>) {
      fetchMapRidMap(
          iFetchPlan,
          fieldValue,
          iCurrentLevel,
          iLevelFromRoot,
          iFieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iContext);
    }
  }

  private static void fetchDocumentRidMap(
      final FetchPlan iFetchPlan,
      Object fieldValue,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchContext iContext) {
    updateRidMap(
        iFetchPlan,
        (EntityImpl) fieldValue,
        iCurrentLevel,
        iLevelFromRoot,
        iFieldDepthLevel,
        parsedRecords,
        iFieldPathFromRoot,
        iContext);
  }

  @SuppressWarnings("unchecked")
  private static void fetchCollectionRidMap(
      final FetchPlan iFetchPlan,
      final Object fieldValue,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchContext iContext) {
    final Iterable<Identifiable> linked = (Iterable<Identifiable>) fieldValue;
    for (Identifiable d : linked) {
      if (d != null) {
        // GO RECURSIVELY
        d = d.getRecord();

        updateRidMap(
            iFetchPlan,
            (EntityImpl) d,
            iCurrentLevel,
            iLevelFromRoot,
            iFieldDepthLevel,
            parsedRecords,
            iFieldPathFromRoot,
            iContext);
      }
    }
  }

  private static void fetchArrayRidMap(
      final FetchPlan iFetchPlan,
      final Object fieldValue,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchContext iContext) {
    if (fieldValue instanceof EntityImpl[] linked) {
      for (EntityImpl d : linked)
      // GO RECURSIVELY
      {
        updateRidMap(
            iFetchPlan,
            d,
            iCurrentLevel,
            iLevelFromRoot,
            iFieldDepthLevel,
            parsedRecords,
            iFieldPathFromRoot,
            iContext);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void fetchMapRidMap(
      final FetchPlan iFetchPlan,
      Object fieldValue,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchContext iContext) {
    final Map<String, EntityImpl> linked = (Map<String, EntityImpl>) fieldValue;
    for (EntityImpl d : (linked).values())
    // GO RECURSIVELY
    {
      updateRidMap(
          iFetchPlan,
          d,
          iCurrentLevel,
          iLevelFromRoot,
          iFieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iContext);
    }
  }

  private static void updateRidMap(
      final FetchPlan iFetchPlan,
      final EntityImpl fieldValue,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchContext iContext) {
    if (fieldValue == null) {
      return;
    }

    final int fetchedLevel = parsedRecords.getInt(fieldValue.getIdentity());
    int currentLevel = iCurrentLevel + 1;
    int fieldDepthLevel = iFieldDepthLevel;
    if (iFetchPlan != null && iFetchPlan.has(iFieldPathFromRoot, iCurrentLevel)) {
      currentLevel = 1;
      fieldDepthLevel = iFetchPlan.getDepthLevel(iFieldPathFromRoot, iCurrentLevel);
    }

    final boolean isEmbedded = isEmbedded(fieldValue);

    if (isEmbedded || fetchedLevel == -1) {
      if (!isEmbedded) {
        parsedRecords.put(fieldValue.getIdentity(), iLevelFromRoot);
      }

      processRecordRidMap(
          fieldValue,
          iFetchPlan,
          currentLevel,
          iLevelFromRoot,
          fieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iContext);
    }
  }

  private static void processRecord(
      final EntityImpl record,
      final Object userObject,
      final FetchPlan fetchPlan,
      final int currentLevel,
      final int levelFromRoot,
      final int fieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String fieldPathFromRoot,
      final FetchListener fetchListener,
      final FetchContext fetchContext,
      final String format) {
    if (record == null) {
      return;
    }
    if (!fetchListener.requireFieldProcessing() && fetchPlan == FetchHelper.DEFAULT_FETCHPLAN) {
      return;
    }
    final RecordSerializerJSON.FormatSettings settings =
        new RecordSerializerJSON.FormatSettings(format);

    // Pre-process to gather fieldTypes
    fetchContext.onBeforeFetch(record);
    if (settings.keepTypes) {
      for (final String fieldName : record.getPropertyNamesInternal()) {
        processFieldTypes(
            record,
            userObject,
            fetchPlan,
            currentLevel,
            fieldDepthLevel,
            fieldPathFromRoot,
            fetchContext,
            format,
            new HashSet<>(),
            fieldName);
      }
      fetchContext.onAfterFetch(record);
    }

    fetchContext.onBeforeFetch(record);
    final Set<String> toRemove = new HashSet<>();
    for (final String fieldName : record.getPropertyNamesInternal()) {
      process(
          record,
          userObject,
          fetchPlan,
          currentLevel,
          levelFromRoot,
          fieldDepthLevel,
          parsedRecords,
          fieldPathFromRoot,
          fetchListener,
          fetchContext,
          format,
          toRemove,
          fieldName);
    }
    for (final String fieldName : toRemove) {
      fetchListener.skipStandardField(record, fieldName, fetchContext, userObject, format);
    }
    if (settings.keepTypes) {
      fetchContext.onAfterFetch(record);
    }
  }

  private static void processFieldTypes(
      EntityImpl record,
      Object userObject,
      FetchPlan fetchPlan,
      int currentLevel,
      int fieldDepthLevel,
      String fieldPathFromRoot,
      FetchContext fetchContext,
      String format,
      Set<String> toRemove,
      String fieldName) {
    Object fieldValue;
    final String fieldPath =
        !fieldPathFromRoot.isEmpty() ? fieldPathFromRoot + "." + fieldName : fieldName;
    int depthLevel;
    depthLevel = getDepthLevel(fetchPlan, fieldPath, currentLevel);
    if (depthLevel == -2) {
      toRemove.add(fieldName);
      return;
    }
    if (fieldDepthLevel > -1) {
      depthLevel = fieldDepthLevel;
    }

    fieldValue = EntityInternalUtils.getRawProperty(record, fieldName);
    final PropertyType fieldType = record.fieldType(fieldName);
    boolean fetch =
        !format.contains("shallow")
            && (!(fieldValue instanceof Identifiable)
            || depthLevel == -1
            || currentLevel <= depthLevel
            || (fetchPlan != null && fetchPlan.has(fieldPath, currentLevel)));
    final boolean isEmbedded = isEmbedded(fieldValue);

    if (!fetch && isEmbedded && fetchContext.fetchEmbeddedDocuments()) {
      // EMBEDDED, GO DEEPER
      fetch = true;
    }

    if (format.contains("shallow")
        || fieldValue == null
        || (!fetch && fieldValue instanceof Identifiable)
        || !(fieldValue instanceof Identifiable)
        && (!(fieldValue.getClass().isArray())
        || Array.getLength(fieldValue) == 0
        || !(Array.get(fieldValue, 0) instanceof Identifiable))
        && !containsIdentifiers(fieldValue)) {
      fetchContext.onBeforeStandardField(fieldValue, fieldName, userObject, fieldType);
    }
  }

  private static void process(
      final EntityImpl record,
      final Object userObject,
      final FetchPlan fetchPlan,
      final int currentLevel,
      final int levelFromRoot,
      final int fieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String fieldPathFromRoot,
      final FetchListener fetchListener,
      final FetchContext fetchContext,
      final String format,
      final Set<String> toRemove,
      final String fieldName) {
    final RecordSerializerJSON.FormatSettings settings =
        new RecordSerializerJSON.FormatSettings(format);

    Object fieldValue;
    final String fieldPath =
        !fieldPathFromRoot.isEmpty() ? fieldPathFromRoot + "." + fieldName : fieldName;
    int depthLevel;
    depthLevel = getDepthLevel(fetchPlan, fieldPath, currentLevel);
    if (depthLevel == -2) {
      toRemove.add(fieldName);
      return;
    }
    if (fieldDepthLevel > -1) {
      depthLevel = fieldDepthLevel;
    }

    fieldValue = EntityInternalUtils.getRawProperty(record, fieldName);
    final PropertyType fieldType = record.fieldType(fieldName);
    boolean fetch =
        !format.contains("shallow")
            && (!(fieldValue instanceof Identifiable)
            || depthLevel == -1
            || currentLevel <= depthLevel
            || (fetchPlan != null && fetchPlan.has(fieldPath, currentLevel)));
    final boolean isEmbedded = isEmbedded(fieldValue);

    if (!fetch && isEmbedded && fetchContext.fetchEmbeddedDocuments()) {
      // EMBEDDED, GO DEEPER
      fetch = true;
    }

    if (format.contains("shallow")
        || fieldValue == null
        || (!fetch && fieldValue instanceof Identifiable)
        || !(fieldValue instanceof Identifiable)
        && (!(fieldValue instanceof Iterable<?>)
        || !((Iterable<?>) fieldValue).iterator().hasNext()
        || !(((Iterable<?>) fieldValue).iterator().next() instanceof Identifiable))
        && (!(fieldValue.getClass().isArray())
        || Array.getLength(fieldValue) == 0
        || !(Array.get(fieldValue, 0) instanceof Identifiable))
        && !containsIdentifiers(fieldValue)) {
      fetchContext.onBeforeStandardField(fieldValue, fieldName, userObject, fieldType);
      fetchListener.processStandardField(
          record, fieldValue, fieldName, fetchContext, userObject, format, fieldType);
      fetchContext.onAfterStandardField(fieldValue, fieldName, userObject, fieldType);
    } else {
      try {
        if (fetch) {
          final int nextLevel = isEmbedded ? levelFromRoot : levelFromRoot + 1;
          fetch(
              record,
              userObject,
              fetchPlan,
              fieldValue,
              fieldName,
              currentLevel,
              nextLevel,
              fieldDepthLevel,
              parsedRecords,
              fieldPath,
              fetchListener,
              fetchContext,
              settings);
        }
      } catch (final Exception e) {
        LogManager.instance()
            .error(FetchHelper.class, "Fetching error on record %s", e, record.getIdentity());
      }
    }
  }

  private static boolean containsIdentifiers(Object fieldValue) {
    if (!MultiValue.isMultiValue(fieldValue)) {
      return false;
    }
    for (Object item : MultiValue.getMultiValueIterable(fieldValue)) {
      if (item instanceof Identifiable) {
        return true;
      }
      if (containsIdentifiers(item)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isEmbedded(Object fieldValue) {
    boolean isEmbedded =
        fieldValue instanceof EntityImpl
            && (((EntityImpl) fieldValue).isEmbedded()
            || !((EntityImpl) fieldValue).getIdentity().isPersistent());

    // ridbag can contain only edges no embedded documents are allowed.
    if (fieldValue instanceof RidBag) {
      return false;
    }
    if (!isEmbedded) {
      try {
        final Object f = MultiValue.getFirstValue(fieldValue);
        isEmbedded =
            f != null
                && (f instanceof EntityImpl
                && (((EntityImpl) f).isEmbedded()
                || !((EntityImpl) f).getIdentity().isPersistent()));
      } catch (Exception e) {
        LogManager.instance().error(FetchHelper.class, "", e);
        // IGNORE IT
      }
    }
    return isEmbedded;
  }

  private static void fetch(
      final EntityImpl iRootRecord,
      final Object iUserObject,
      final FetchPlan iFetchPlan,
      final Object fieldValue,
      final String fieldName,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchListener iListener,
      final FetchContext iContext,
      final RecordSerializerJSON.FormatSettings settings)
      throws IOException {
    int currentLevel = iCurrentLevel + 1;
    int fieldDepthLevel = iFieldDepthLevel;
    if (iFetchPlan != null && iFetchPlan.has(iFieldPathFromRoot, iCurrentLevel)) {
      currentLevel = 0;
      fieldDepthLevel = iFetchPlan.getDepthLevel(iFieldPathFromRoot, iCurrentLevel);
    }

    if (fieldValue == null) {
      iListener.processStandardField(iRootRecord, null, fieldName, iContext, iUserObject, "", null);
    } else if (fieldValue instanceof Identifiable) {
      fetchDocument(
          iRootRecord,
          iUserObject,
          iFetchPlan,
          (Identifiable) fieldValue,
          fieldName,
          currentLevel,
          iLevelFromRoot,
          fieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iListener,
          iContext,
          settings);
    } else if (fieldValue instanceof Map<?, ?>) {
      fetchMap(
          iRootRecord,
          iUserObject,
          iFetchPlan,
          fieldValue,
          fieldName,
          currentLevel,
          iLevelFromRoot,
          fieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iListener,
          iContext,
          settings);
    } else if (MultiValue.isMultiValue(fieldValue)) {
      fetchCollection(
          iRootRecord,
          iUserObject,
          iFetchPlan,
          fieldValue,
          fieldName,
          currentLevel,
          iLevelFromRoot,
          fieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iListener,
          iContext,
          settings);
    } else if (fieldValue.getClass().isArray()) {
      fetchArray(
          iRootRecord,
          iUserObject,
          iFetchPlan,
          fieldValue,
          fieldName,
          currentLevel,
          iLevelFromRoot,
          fieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iListener,
          iContext,
          settings);
    }
  }

  @SuppressWarnings("unchecked")
  private static void fetchMap(
      final EntityImpl iRootRecord,
      final Object iUserObject,
      final FetchPlan iFetchPlan,
      Object fieldValue,
      String fieldName,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchListener iListener,
      final FetchContext iContext,
      final RecordSerializerJSON.FormatSettings settings) {
    final Map<String, Object> linked = (Map<String, Object>) fieldValue;
    iContext.onBeforeMap(iRootRecord, fieldName, iUserObject);

    for (Object key : linked.keySet()) {
      final Object o = linked.get(key.toString());

      if (o instanceof Identifiable identifiable) {
        DBRecord r = null;
        try {
          r = identifiable.getRecord();
        } catch (RecordNotFoundException ignore) {
        }
        if (r != null) {
          if (r instanceof EntityImpl d) {
            // GO RECURSIVELY
            final int fieldDepthLevel = parsedRecords.getInt(d.getIdentity());
            if (!d.getIdentity().isValid()
                || (fieldDepthLevel > -1 && fieldDepthLevel == iLevelFromRoot)) {
              removeParsedFromMap(parsedRecords, d);
              iContext.onBeforeDocument(iRootRecord, d, key.toString(), iUserObject);
              final Object userObject =
                  iListener.fetchLinkedMapEntry(
                      iRootRecord, iUserObject, fieldName, key.toString(), d, iContext);
              processRecord(
                  d,
                  userObject,
                  iFetchPlan,
                  iCurrentLevel,
                  iLevelFromRoot,
                  iFieldDepthLevel,
                  parsedRecords,
                  iFieldPathFromRoot,
                  iListener,
                  iContext,
                  getTypesFormat(settings.keepTypes)); // ""
              iContext.onAfterDocument(iRootRecord, d, key.toString(), iUserObject);
            } else {
              iListener.parseLinked(iRootRecord, d, iUserObject, key.toString(), iContext);
            }
          } else {
            iListener.parseLinked(iRootRecord, r, iUserObject, key.toString(), iContext);
          }

        } else {
          iListener.processStandardField(
              iRootRecord, o, key.toString(), iContext, iUserObject, "", null);
        }
      } else {
        iListener.processStandardField(
            iRootRecord, o, key.toString(), iContext, iUserObject, "", null);
      }
    }
    iContext.onAfterMap(iRootRecord, fieldName, iUserObject);
  }

  private static void fetchArray(
      final EntityImpl rootRecord,
      final Object iUserObject,
      final FetchPlan iFetchPlan,
      Object fieldValue,
      String fieldName,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchListener iListener,
      final FetchContext context,
      RecordSerializerJSON.FormatSettings settings) {
    if (fieldValue instanceof EntityImpl[] linked) {
      context.onBeforeArray(rootRecord, fieldName, iUserObject, linked);
      for (final EntityImpl entity : linked) {
        // GO RECURSIVELY
        final int fieldDepthLevel = parsedRecords.getInt(entity.getIdentity());
        if (!entity.getIdentity().isValid()
            || (fieldDepthLevel > -1 && fieldDepthLevel == iLevelFromRoot)) {
          removeParsedFromMap(parsedRecords, entity);
          context.onBeforeDocument(rootRecord, entity, fieldName, iUserObject);
          final Object userObject =
              iListener.fetchLinked(rootRecord, iUserObject, fieldName, entity, context);
          processRecord(
              entity,
              userObject,
              iFetchPlan,
              iCurrentLevel,
              iLevelFromRoot,
              iFieldDepthLevel,
              parsedRecords,
              iFieldPathFromRoot,
              iListener,
              context,
              getTypesFormat(settings.keepTypes)); // ""
          context.onAfterDocument(rootRecord, entity, fieldName, iUserObject);
        } else {
          iListener.parseLinkedCollectionValue(
              rootRecord, entity, iUserObject, fieldName, context);
        }
      }
      context.onAfterArray(rootRecord, fieldName, iUserObject);
    } else {
      iListener.processStandardField(
          rootRecord, fieldValue, fieldName, context, iUserObject, "", null);
    }
  }

  @SuppressWarnings("unchecked")
  private static void fetchCollection(
      final EntityImpl iRootRecord,
      final Object iUserObject,
      final FetchPlan iFetchPlan,
      final Object fieldValue,
      final String fieldName,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchListener iListener,
      final FetchContext context,
      final RecordSerializerJSON.FormatSettings settings)
      throws IOException {
    final Iterable<?> linked;
    if (fieldValue instanceof Iterable<?>) {
      linked = (Iterable<Identifiable>) fieldValue;
      context.onBeforeCollection(iRootRecord, fieldName, iUserObject, linked);
    } else if (fieldValue.getClass().isArray()) {
      linked = MultiValue.getMultiValueIterable(fieldValue);
      context.onBeforeCollection(iRootRecord, fieldName, iUserObject, linked);
    } else if (fieldValue instanceof Map<?, ?>) {
      linked = ((Map<?, ?>) fieldValue).values();
      context.onBeforeMap(iRootRecord, fieldName, iUserObject);
    } else {
      throw new IllegalStateException("Unrecognized type: " + fieldValue.getClass());
    }

    final Iterator<?> iter = linked.iterator();

    try {
      while (iter.hasNext()) {
        final Object recordLazyMultiValue = iter.next();
        if (recordLazyMultiValue == null) {
          continue;
        }

        if (recordLazyMultiValue instanceof Identifiable identifiable) {
          // GO RECURSIVELY
          final int fieldDepthLevel = parsedRecords.getInt(identifiable.getIdentity());
          if (!identifiable.getIdentity().isPersistent()
              || (fieldDepthLevel > -1 && fieldDepthLevel == iLevelFromRoot)) {
            removeParsedFromMap(parsedRecords, identifiable);
            try {
              identifiable = identifiable.getRecord();
              if (!(identifiable instanceof EntityImpl)) {
                iListener.processStandardField(
                    null, identifiable, fieldName, context, iUserObject, "", null);
              } else {
                context.onBeforeDocument(
                    iRootRecord, (EntityImpl) identifiable, fieldName, iUserObject);
                final Object userObject =
                    iListener.fetchLinkedCollectionValue(
                        iRootRecord, iUserObject, fieldName, (EntityImpl) identifiable, context);
                processRecord(
                    (EntityImpl) identifiable,
                    userObject,
                    iFetchPlan,
                    iCurrentLevel,
                    iLevelFromRoot,
                    iFieldDepthLevel,
                    parsedRecords,
                    iFieldPathFromRoot,
                    iListener,
                    context,
                    getTypesFormat(settings.keepTypes)); // ""
                context.onAfterDocument(
                    iRootRecord, (EntityImpl) identifiable, fieldName, iUserObject);
              }
            } catch (RecordNotFoundException rnf) {
              iListener.processStandardField(
                  null, identifiable, null, context, iUserObject, "", null);
            }
          } else {
            iListener.parseLinkedCollectionValue(
                iRootRecord, identifiable, iUserObject, fieldName, context);
          }
        } else if (recordLazyMultiValue instanceof Map<?, ?>) {
          fetchMap(
              iRootRecord,
              iUserObject,
              iFetchPlan,
              recordLazyMultiValue,
              null,
              iCurrentLevel + 1,
              iLevelFromRoot,
              iFieldDepthLevel,
              parsedRecords,
              iFieldPathFromRoot,
              iListener,
              context,
              settings);
        } else if (MultiValue.isMultiValue(recordLazyMultiValue)) {
          fetchCollection(
              iRootRecord,
              iUserObject,
              iFetchPlan,
              recordLazyMultiValue,
              null,
              iCurrentLevel + 1,
              iLevelFromRoot,
              iFieldDepthLevel,
              parsedRecords,
              iFieldPathFromRoot,
              iListener,
              context,
              settings);
        } else if ((recordLazyMultiValue instanceof String
            || recordLazyMultiValue instanceof Number
            || recordLazyMultiValue instanceof Boolean)
            && context instanceof JSONFetchContext) {
          ((JSONFetchContext) context).getJsonWriter().writeValue(0, false, recordLazyMultiValue);
        }
      }
    } finally {
      if (fieldValue instanceof Iterable<?>) {
        context.onAfterCollection(iRootRecord, fieldName, iUserObject);
      } else if (fieldValue.getClass().isArray()) {
        context.onAfterCollection(iRootRecord, fieldName, iUserObject);
      } else if (fieldValue instanceof Map<?, ?>) {
        context.onAfterMap(iRootRecord, fieldName, iUserObject);
      }
    }
  }

  private static void fetchDocument(
      final EntityImpl iRootRecord,
      final Object iUserObject,
      final FetchPlan iFetchPlan,
      final Identifiable fieldValue,
      final String fieldName,
      final int iCurrentLevel,
      final int iLevelFromRoot,
      final int iFieldDepthLevel,
      final Object2IntOpenHashMap<RID> parsedRecords,
      final String iFieldPathFromRoot,
      final FetchListener iListener,
      final FetchContext iContext,
      final RecordSerializerJSON.FormatSettings settings) {
    if (fieldValue instanceof RID && !((RecordId) fieldValue).isValid()) {
      // RID NULL: TREAT AS "NULL" VALUE
      iContext.onBeforeStandardField(fieldValue, fieldName, iRootRecord, null);
      iListener.parseLinked(iRootRecord, fieldValue, iUserObject, fieldName, iContext);
      iContext.onAfterStandardField(fieldValue, fieldName, iRootRecord, null);
      return;
    }

    final int fieldDepthLevel = parsedRecords.getInt(fieldValue.getIdentity());
    if (!((RecordId) fieldValue.getIdentity()).isValid()
        || (fieldDepthLevel > -1 && fieldDepthLevel == iLevelFromRoot)) {
      removeParsedFromMap(parsedRecords, fieldValue);
      final EntityImpl linked;
      try {
        linked = fieldValue.getRecord();
      } catch (RecordNotFoundException rnf) {
        return;
      }

      iContext.onBeforeDocument(iRootRecord, linked, fieldName, iUserObject);
      Object userObject =
          iListener.fetchLinked(iRootRecord, iUserObject, fieldName, linked, iContext);
      processRecord(
          linked,
          userObject,
          iFetchPlan,
          iCurrentLevel,
          iLevelFromRoot,
          iFieldDepthLevel,
          parsedRecords,
          iFieldPathFromRoot,
          iListener,
          iContext,
          getTypesFormat(settings.keepTypes)); // ""
      iContext.onAfterDocument(iRootRecord, linked, fieldName, iUserObject);
    } else {
      iContext.onBeforeStandardField(fieldValue, fieldName, iRootRecord, null);
      iListener.parseLinked(iRootRecord, fieldValue, iUserObject, fieldName, iContext);
      iContext.onAfterStandardField(fieldValue, fieldName, iRootRecord, null);
    }
  }

  private static String getTypesFormat(final boolean keepTypes) {
    final StringBuilder sb = new StringBuilder();
    if (keepTypes) {
      sb.append("keepTypes");
    }
    return sb.toString();
  }

  protected static void removeParsedFromMap(
      final Object2IntOpenHashMap<RID> parsedRecords, Identifiable d) {
    parsedRecords.removeInt(d.getIdentity());
  }
}
