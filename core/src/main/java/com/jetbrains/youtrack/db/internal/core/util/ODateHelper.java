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

package com.jetbrains.youtrack.db.internal.core.util;

import com.jetbrains.youtrack.db.internal.core.config.OStorageConfiguration;
import com.jetbrains.youtrack.db.internal.core.db.ODatabaseRecordThreadLocal;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class ODateHelper {

  public static Calendar getDatabaseCalendar() {
    return Calendar.getInstance(getDatabaseTimeZone());
  }

  public static Calendar getDatabaseCalendar(final YTDatabaseSessionInternal db) {
    return Calendar.getInstance(getDatabaseTimeZone(db));
  }

  public static TimeZone getDatabaseTimeZone() {
    return getDatabaseTimeZone(ODatabaseRecordThreadLocal.instance().getIfDefined());
  }

  public static TimeZone getDatabaseTimeZone(final YTDatabaseSessionInternal db) {
    if (db != null && !db.isClosed()) {
      return db.getStorageInfo().getConfiguration().getTimeZone();
    }
    return TimeZone.getDefault();
  }

  public static DateFormat getDateFormatInstance() {
    return getDateFormatInstance(ODatabaseRecordThreadLocal.instance().getIfDefined());
  }

  public static DateFormat getDateFormatInstance(final YTDatabaseSessionInternal db) {
    if (db != null && !db.isClosed()) {
      return db.getStorageInfo().getConfiguration().getDateFormatInstance();
    } else {
      SimpleDateFormat format = new SimpleDateFormat(OStorageConfiguration.DEFAULT_DATE_FORMAT);
      format.setTimeZone(getDatabaseTimeZone());
      return format;
    }
  }

  public static String getDateFormat() {
    return getDateFormat(ODatabaseRecordThreadLocal.instance().getIfDefined());
  }

  public static String getDateFormat(final YTDatabaseSessionInternal db) {
    if (db != null && !db.isClosed()) {
      return db.getStorageInfo().getConfiguration().getDateFormat();
    } else {
      return OStorageConfiguration.DEFAULT_DATE_FORMAT;
    }
  }

  public static DateFormat getDateTimeFormatInstance() {
    return getDateTimeFormatInstance(ODatabaseRecordThreadLocal.instance().getIfDefined());
  }

  public static DateFormat getDateTimeFormatInstance(final YTDatabaseSessionInternal db) {
    if (db != null && !db.isClosed()) {
      return db.getStorageInfo().getConfiguration().getDateTimeFormatInstance();
    } else {
      SimpleDateFormat format = new SimpleDateFormat(OStorageConfiguration.DEFAULT_DATETIME_FORMAT);
      format.setTimeZone(getDatabaseTimeZone());
      return format;
    }
  }

  public static String getDateTimeFormat() {
    return getDateTimeFormat(ODatabaseRecordThreadLocal.instance().getIfDefined());
  }

  public static String getDateTimeFormat(final YTDatabaseSessionInternal db) {
    if (db != null && !db.isClosed()) {
      return db.getStorageInfo().getConfiguration().getDateTimeFormat();
    } else {
      return OStorageConfiguration.DEFAULT_DATETIME_FORMAT;
    }
  }

  public static Date now() {
    return new Date();
  }
}