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
package com.jetbrains.youtrack.db.internal.core.metadata.security;

import com.jetbrains.youtrack.db.api.exception.ConfigurationException;
import com.jetbrains.youtrack.db.api.exception.RecordNotFoundException;
import com.jetbrains.youtrack.db.api.record.Identifiable;
import com.jetbrains.youtrack.db.api.security.SecurityUser;
import com.jetbrains.youtrack.db.internal.core.db.DatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.id.RecordId;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.SchemaImmutableClass;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityInternalUtils;
import java.util.Set;

/**
 * Checks the access against restricted resources. Restricted resources are those documents of
 * classes that implement ORestricted abstract class.
 */
public class RestrictedAccessHook {

  public static boolean onRecordBeforeCreate(
      final EntityImpl entity, DatabaseSessionInternal database) {
    final SchemaImmutableClass cls = EntityInternalUtils.getImmutableSchemaClass(database, entity);
    if (cls != null && cls.isRestricted()) {
      String fieldNames = cls.getCustom(SecurityShared.ONCREATE_FIELD);
      if (fieldNames == null) {
        fieldNames = RestrictedOperation.ALLOW_ALL.getFieldName();
      }
      final String[] fields = fieldNames.split(",");
      String identityType = cls.getCustom(SecurityShared.ONCREATE_IDENTITY_TYPE);
      if (identityType == null) {
        identityType = "user";
      }

      Identifiable identity = null;
      if (identityType.equals("user")) {
        final SecurityUser user = database.geCurrentUser();
        if (user != null) {
          identity = user.getIdentity(database);
        }
      } else if (identityType.equals("role")) {
        final Set<? extends SecurityRole> roles = database.geCurrentUser().getRoles();
        if (!roles.isEmpty()) {
          identity = roles.iterator().next().getIdentity(database);
        }
      } else {
        throw new ConfigurationException(
            "Wrong custom field '"
                + SecurityShared.ONCREATE_IDENTITY_TYPE
                + "' in class '"
                + cls.getName()
                + "' with value '"
                + identityType
                + "'. Supported ones are: 'user', 'role'");
      }

      if (identity != null && ((RecordId) identity.getIdentity()).isValid()) {
        for (String f : fields) {
          database.getSharedContext().getSecurity().allowIdentity(database, entity, f, identity);
        }
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  public static boolean isAllowed(
      DatabaseSessionInternal database,
      final EntityImpl ent,
      final RestrictedOperation iAllowOperation,
      final boolean iReadOriginal) {
    final SchemaImmutableClass cls = EntityInternalUtils.getImmutableSchemaClass(database, ent);
    if (cls != null && cls.isRestricted()) {

      if (database.geCurrentUser() == null) {
        return true;
      }

      if (database.geCurrentUser()
          .isRuleDefined(database, Rule.ResourceGeneric.BYPASS_RESTRICTED, null)) {
        if (database
            .geCurrentUser()
            .checkIfAllowed(database,
                Rule.ResourceGeneric.BYPASS_RESTRICTED, null, Role.PERMISSION_READ)
            != null)
        // BYPASS RECORD LEVEL SECURITY: ONLY "ADMIN" ROLE CAN BY DEFAULT
        {
          return true;
        }
      }

      final EntityImpl entity;
      if (iReadOriginal)
      // RELOAD TO AVOID HACKING OF "_ALLOW" FIELDS
      {
        try {
          entity = database.load(ent.getIdentity());
        } catch (RecordNotFoundException e) {
          return false;
        }

      } else {
        entity = ent;
      }

      return database
          .getMetadata()
          .getSecurity()
          .isAllowed(
              entity.field(RestrictedOperation.ALLOW_ALL.getFieldName()),
              entity.field(iAllowOperation.getFieldName()));
    }

    return true;
  }
}
