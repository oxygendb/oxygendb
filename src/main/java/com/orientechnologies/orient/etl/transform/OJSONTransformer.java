/*
 *
 *  * Copyright 2010-2014 Orient Technologies LTD (info(at)orientechnologies.com)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.orientechnologies.orient.etl.transform;

import com.orientechnologies.orient.core.record.impl.ODocument;

import java.util.NoSuchElementException;

public class OJSONTransformer extends OAbstractTransformer {
  @Override
  public String getName() {
    return "json2document";
  }

  @Override
  public Object next() {
    if (!hasNext())
      throw new NoSuchElementException("EOF");

    try {
      if (obj instanceof ODocument)
        return obj;
      else if (obj instanceof String)
        return new ODocument((String) obj);
      else
        throw new OTransformException("Unknown input '" + obj + "' of class '" + obj.getClass() + "'");

    } finally {
      obj = null;
    }
  }
}
