package com.jetbrains.youtrack.db.internal.common.serialization;

import org.junit.Before;

/**
 * @since 21.05.13
 */
public class UnsafeConverterTest extends AbstractConverterTest {

  @Before
  public void beforeClass() {
    converter = new UnsafeBinaryConverter();
  }

  @Override
  public void testPutIntBigEndian() {
    super.testPutIntBigEndian();
  }

  @Override
  public void testPutIntLittleEndian() {
    super.testPutIntLittleEndian();
  }

  @Override
  public void testPutLongBigEndian() {
    super.testPutLongBigEndian();
  }

  @Override
  public void testPutLongLittleEndian() {
    super.testPutLongLittleEndian();
  }

  @Override
  public void testPutShortBigEndian() {
    super.testPutShortBigEndian();
  }

  @Override
  public void testPutShortLittleEndian() {
    super.testPutShortLittleEndian();
  }

  @Override
  public void testPutCharBigEndian() {
    super.testPutCharBigEndian();
  }

  @Override
  public void testPutCharLittleEndian() {
    super.testPutCharLittleEndian();
  }
}
