/*
 * Copyright 2010-2012 henryzhao81-at-gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.orientechnologies.orient.core.schedule;

import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.ODatabaseSessionInternal;
import com.orientechnologies.orient.core.metadata.function.OFunction;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.type.ODocumentWrapper;
import java.util.Date;
import java.util.Map;

/**
 * Builds a OSchedulerEvent with a fluent interface
 *
 * @since v2.2
 */
public class OScheduledEventBuilder extends ODocumentWrapper {

  public OScheduledEventBuilder() {
    super(new ODocument(OScheduledEvent.CLASS_NAME));
  }

  /**
   * Creates a scheduled event object from a configuration.
   */
  public OScheduledEventBuilder(final ODocument doc) {
    super(doc);
  }

  public OScheduledEventBuilder setFunction(ODatabaseSession session, final OFunction function) {
    getDocument(session).field(OScheduledEvent.PROP_FUNC, function);
    return this;
  }

  public OScheduledEventBuilder setRule(ODatabaseSession session, final String rule) {
    getDocument(session).field(OScheduledEvent.PROP_RULE, rule);
    return this;
  }

  public OScheduledEventBuilder setArguments(ODatabaseSession session,
      final Map<Object, Object> arguments) {
    getDocument(session).field(OScheduledEvent.PROP_ARGUMENTS, arguments);
    return this;
  }

  public OScheduledEventBuilder setStartTime(ODatabaseSession session, final Date startTime) {
    getDocument(session).field(OScheduledEvent.PROP_STARTTIME, startTime);
    return this;
  }

  public OScheduledEvent build(ODatabaseSessionInternal session) {
    var event = new OScheduledEvent(getDocument(session), session);
    event.save(session);
    return event;
  }

  public String toString() {
    var db = ODatabaseRecordThreadLocal.instance().getIfDefined();
    if (db != null) {
      return getDocument(db).toString();
    }
    return super.toString();
  }

  public OScheduledEventBuilder setName(ODatabaseSession session, final String name) {
    getDocument(session).field(OScheduledEvent.PROP_NAME, name);
    return this;
  }
}
