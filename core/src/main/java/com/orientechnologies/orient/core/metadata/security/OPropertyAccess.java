package com.orientechnologies.orient.core.metadata.security;

import com.orientechnologies.orient.core.db.ODatabaseSessionInternal;
import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.Set;

public class OPropertyAccess {

  private final Set<String> filtered;

  public OPropertyAccess(ODatabaseSessionInternal session, ODocument document,
      OSecurityInternal security) {
    filtered = security.getFilteredProperties(session, document);
  }

  public OPropertyAccess(Set<String> filtered) {
    this.filtered = filtered;
  }

  public boolean hasFilters() {
    return filtered != null && !filtered.isEmpty();
  }

  public boolean isReadable(String property) {
    return filtered == null || !filtered.contains(property);
  }
}
