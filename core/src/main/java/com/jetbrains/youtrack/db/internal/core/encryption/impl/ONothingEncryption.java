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

package com.jetbrains.youtrack.db.internal.core.encryption.impl;

import com.jetbrains.youtrack.db.internal.core.encryption.OEncryption;

/**
 * No encryption.
 */
public class ONothingEncryption implements OEncryption {

  public static final String NAME = "nothing";

  public static final ONothingEncryption INSTANCE = new ONothingEncryption();

  @Override
  public byte[] encrypt(final byte[] content) {
    return content;
  }

  @Override
  public byte[] decrypt(final byte[] content) {
    return content;
  }

  @Override
  public byte[] encrypt(final byte[] content, final int offset, final int length) {
    if (offset == 0 && length == content.length) {
      return content;
    }

    byte[] result = new byte[length];
    System.arraycopy(content, offset, result, 0, length);

    return result;
  }

  @Override
  public byte[] decrypt(final byte[] content, final int offset, final int length) {
    if (offset == 0 && length == content.length) {
      return content;
    }

    byte[] result = new byte[length];
    System.arraycopy(content, offset, result, 0, length);

    return result;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public OEncryption configure(String iOptions) {
    return null;
  }
}