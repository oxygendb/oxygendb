/*
 *
 *
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

package com.orientechnologies.lucene.test;

import com.orientechnologies.DBTestBase;
import com.orientechnologies.common.io.OIOUtils;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
public abstract class BaseLuceneTest extends DBTestBase {

  protected static String getScriptFromStream(final InputStream scriptStream) {
    try {
      return OIOUtils.readStreamAsString(scriptStream);
    } catch (final IOException e) {
      throw new RuntimeException("Could not read script stream.", e);
    }
  }
}
