package com.jetbrains.youtrack.db.internal.core.storage.index.hashindex.local.v3;

import com.jetbrains.youtrack.db.internal.DBTestBase;
import com.jetbrains.youtrack.db.internal.common.io.FileUtils;
import com.jetbrains.youtrack.db.internal.common.serialization.types.OIntegerSerializer;
import com.jetbrains.youtrack.db.internal.core.OCreateDatabaseUtil;
import com.jetbrains.youtrack.db.internal.core.config.GlobalConfiguration;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSession;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDB;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDBConfig;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTType;
import com.jetbrains.youtrack.db.internal.core.serialization.serializer.binary.OBinarySerializerFactory;
import com.jetbrains.youtrack.db.internal.core.storage.cache.OCacheEntry;
import com.jetbrains.youtrack.db.internal.core.storage.cache.OReadCache;
import com.jetbrains.youtrack.db.internal.core.storage.cache.OWriteCache;
import com.jetbrains.youtrack.db.internal.core.storage.cluster.OClusterPage;
import com.jetbrains.youtrack.db.internal.core.storage.disk.LocalPaginatedStorage;
import com.jetbrains.youtrack.db.internal.core.storage.fs.OFile;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.AbstractPaginatedStorage;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.base.ODurablePage;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.OAtomicUnitEndRecord;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.OAtomicUnitStartRecord;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.OFileCreatedWALRecord;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.OLogSequenceNumber;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.ONonTxOperationPerformedWALRecord;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.OOperationUnitBodyRecord;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.OUpdatePageRecord;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.OWALRecord;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.cas.CASDiskWriteAheadLog;
import com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated.wal.common.WriteableWALRecord;
import com.jetbrains.youtrack.db.internal.core.storage.index.hashindex.local.OMurmurHash3HashFunction;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

/**
 * @since 5/19/14
 */
public class OLocalHashTableV3WALTestIT extends OLocalHashTableV3Base {

  private static final String ACTUAL_DB_NAME =
      OLocalHashTableV3WALTestIT.class.getSimpleName() + "Actual";
  private static final String EXPECTED_DB_NAME =
      OLocalHashTableV3WALTestIT.class.getSimpleName() + "Expected";

  private LocalPaginatedStorage actualStorage;
  private LocalPaginatedStorage expectedStorage;

  private String actualStorageDir;
  private String expectedStorageDir;

  private YTDatabaseSession databaseDocumentTx;

  private OWriteCache actualWriteCache;

  private YTDatabaseSession expectedDatabaseDocumentTx;
  private OWriteCache expectedWriteCache;

  private YouTrackDB youTrackDB;

  @Before
  public void before() throws IOException {
    String buildDirectory = System.getProperty("buildDirectory", ".");

    buildDirectory += "/" + this.getClass().getSimpleName();

    final java.io.File buildDir = new java.io.File(buildDirectory);
    FileUtils.deleteRecursively(buildDir);

    youTrackDB =
        new YouTrackDB(
            DBTestBase.embeddedDBUrl(getClass()),
            YouTrackDBConfig.builder()
                .addConfig(GlobalConfiguration.CREATE_DEFAULT_USERS, false)
                .build());

    OCreateDatabaseUtil.createDatabase(ACTUAL_DB_NAME, youTrackDB, OCreateDatabaseUtil.TYPE_PLOCAL);
    // youTrackDB.create(ACTUAL_DB_NAME, ODatabaseType.PLOCAL);
    databaseDocumentTx =
        youTrackDB.open(ACTUAL_DB_NAME, "admin", OCreateDatabaseUtil.NEW_ADMIN_PASSWORD);
    ((YTDatabaseSessionInternal) databaseDocumentTx).getSharedContext().getViewManager().close();

    OCreateDatabaseUtil.createDatabase(EXPECTED_DB_NAME, youTrackDB,
        OCreateDatabaseUtil.TYPE_PLOCAL);
    // youTrackDB.create(EXPECTED_DB_NAME, ODatabaseType.PLOCAL);
    expectedDatabaseDocumentTx =
        youTrackDB.open(EXPECTED_DB_NAME, "admin", OCreateDatabaseUtil.NEW_ADMIN_PASSWORD);
    ((YTDatabaseSessionInternal) expectedDatabaseDocumentTx)
        .getSharedContext()
        .getViewManager()
        .close();
    expectedStorage =
        ((LocalPaginatedStorage)
            ((YTDatabaseSessionInternal) expectedDatabaseDocumentTx).getStorage());
    actualStorage =
        (LocalPaginatedStorage) ((YTDatabaseSessionInternal) databaseDocumentTx).getStorage();

    atomicOperationsManager = actualStorage.getAtomicOperationsManager();

    actualStorageDir = actualStorage.getStoragePath().toString();
    expectedStorageDir = expectedStorage.getStoragePath().toString();

    actualWriteCache =
        ((LocalPaginatedStorage) ((YTDatabaseSessionInternal) databaseDocumentTx).getStorage())
            .getWriteCache();
    expectedWriteCache =
        ((LocalPaginatedStorage)
            ((YTDatabaseSessionInternal) expectedDatabaseDocumentTx).getStorage())
            .getWriteCache();

    CASDiskWriteAheadLog diskWriteAheadLog = (CASDiskWriteAheadLog) actualStorage.getWALInstance();

    actualStorage.synch();
    diskWriteAheadLog.addCutTillLimit(diskWriteAheadLog.getFlushedLsn());

    createActualHashTable();
  }

  @After
  public void after() {
    youTrackDB.drop(ACTUAL_DB_NAME);
    youTrackDB.drop(EXPECTED_DB_NAME);
    youTrackDB.close();
  }

  private void createActualHashTable() throws IOException {
    OMurmurHash3HashFunction<Integer> murmurHash3HashFunction =
        new OMurmurHash3HashFunction<>(OIntegerSerializer.INSTANCE);

    localHashTable =
        new LocalHashTableV3<>(
            "actualLocalHashTable",
            ".imc",
            ".tsc",
            ".obf",
            ".nbh",
            (AbstractPaginatedStorage)
                ((YTDatabaseSessionInternal) databaseDocumentTx).getStorage());
    atomicOperationsManager.executeInsideAtomicOperation(
        null,
        atomicOperation ->
            localHashTable.create(
                atomicOperation,
                OIntegerSerializer.INSTANCE,
                OBinarySerializerFactory.getInstance().getObjectSerializer(YTType.STRING),
                null,
                null,
                murmurHash3HashFunction,
                true));
  }

  @Override
  public void testKeyPut() throws IOException {
    super.testKeyPut();

    Assert.assertNull(atomicOperationsManager.getCurrentOperation());

    assertFileRestoreFromWAL();
  }

  @Override
  public void testKeyPutRandomUniform() throws IOException {
    super.testKeyPutRandomUniform();

    Assert.assertNull(atomicOperationsManager.getCurrentOperation());

    assertFileRestoreFromWAL();
  }

  @Override
  public void testKeyPutRandomGaussian() throws IOException {
    super.testKeyPutRandomGaussian();

    Assert.assertNull(atomicOperationsManager.getCurrentOperation());

    assertFileRestoreFromWAL();
  }

  @Override
  public void testKeyDelete() throws IOException {
    super.testKeyDelete();

    Assert.assertNull(atomicOperationsManager.getCurrentOperation());

    assertFileRestoreFromWAL();
  }

  @Override
  public void testKeyDeleteRandomGaussian() throws IOException {
    super.testKeyDeleteRandomGaussian();

    Assert.assertNull(atomicOperationsManager.getCurrentOperation());

    assertFileRestoreFromWAL();
  }

  @Override
  public void testKeyAddDelete() throws IOException {
    super.testKeyAddDelete();

    Assert.assertNull(atomicOperationsManager.getCurrentOperation());

    assertFileRestoreFromWAL();
  }

  @Override
  public void testKeyPutRemoveNullKey() throws IOException {
    super.testKeyPutRemoveNullKey();

    Assert.assertNull(atomicOperationsManager.getCurrentOperation());

    assertFileRestoreFromWAL();
  }

  private void assertFileRestoreFromWAL() throws IOException {
    final long imcFileId = actualWriteCache.fileIdByName(localHashTable.getName() + ".imc");
    final String nativeImcFileName = actualWriteCache.nativeFileNameById(imcFileId);

    final long tscFileId = actualWriteCache.fileIdByName(localHashTable.getName() + ".tsc");
    final String nativeTscFileName = actualWriteCache.nativeFileNameById(tscFileId);

    final long nbhFileId = actualWriteCache.fileIdByName(localHashTable.getName() + ".nbh");
    final String nativeNBHFileName = actualWriteCache.nativeFileNameById(nbhFileId);

    final long obfFileId = actualWriteCache.fileIdByName(localHashTable.getName() + ".obf");
    final String nativeOBFFileName = actualWriteCache.nativeFileNameById(obfFileId);

    localHashTable.close();

    databaseDocumentTx.activateOnCurrentThread();
    databaseDocumentTx.close();
    actualStorage.shutdown();

    System.out.println("Start data restore");
    restoreDataFromWAL();
    System.out.println("Stop data restore");

    final long expectedImcFileId = expectedWriteCache.fileIdByName("expectedLocalHashTable.imc");
    final String nativeExpectedImcFileName =
        expectedWriteCache.nativeFileNameById(expectedImcFileId);

    final long expectedTscFileId = expectedWriteCache.fileIdByName("expectedLocalHashTable.tsc");
    final String nativeExpectedTscFileName =
        expectedWriteCache.nativeFileNameById(expectedTscFileId);

    final long expectedNbhFileId = expectedWriteCache.fileIdByName("expectedLocalHashTable.nbh");
    final String nativeExpectedNBHFileName =
        expectedWriteCache.nativeFileNameById(expectedNbhFileId);

    final long expectedObfFileId = expectedWriteCache.fileIdByName("expectedLocalHashTable.obf");
    final String nativeExpectedOBFFile = expectedWriteCache.nativeFileNameById(expectedObfFileId);

    expectedDatabaseDocumentTx.activateOnCurrentThread();
    expectedDatabaseDocumentTx.close();
    expectedStorage.shutdown();

    System.out.println("Start data comparison");

    assertFileContentIsTheSame(
        nativeExpectedImcFileName,
        nativeImcFileName,
        nativeExpectedTscFileName,
        nativeTscFileName,
        nativeExpectedNBHFileName,
        nativeNBHFileName,
        nativeExpectedOBFFile,
        nativeOBFFileName);

    System.out.println("Stop data comparison");
  }

  private void restoreDataFromWAL() throws IOException {
    final OReadCache expectedReadCache =
        ((AbstractPaginatedStorage)
            ((YTDatabaseSessionInternal) expectedDatabaseDocumentTx).getStorage())
            .getReadCache();

    CASDiskWriteAheadLog log =
        new CASDiskWriteAheadLog(
            ACTUAL_DB_NAME,
            Paths.get(actualStorageDir),
            Paths.get(actualStorageDir),
            10_000,
            128,
            null,
            null,
            30 * 60 * 1_000_000_000L,
            100 * 1024 * 1024,
            1000,
            false,
            Locale.ENGLISH,
            -1,
            1_000,
            false,
            true,
            false,
            0);
    OLogSequenceNumber lsn = log.begin();

    List<OWALRecord> atomicUnit = new ArrayList<>();
    List<WriteableWALRecord> walRecords = log.read(lsn, 1_000);

    boolean atomicChangeIsProcessed = false;
    while (!walRecords.isEmpty()) {
      for (WriteableWALRecord walRecord : walRecords) {
        if (walRecord instanceof OOperationUnitBodyRecord) {
          atomicUnit.add(walRecord);
        }

        if (!atomicChangeIsProcessed) {
          if (walRecord instanceof OAtomicUnitStartRecord) {
            atomicChangeIsProcessed = true;
          }
        } else if (walRecord instanceof OAtomicUnitEndRecord) {
          atomicChangeIsProcessed = false;

          for (OWALRecord restoreRecord : atomicUnit) {
            if (restoreRecord instanceof OAtomicUnitStartRecord
                || restoreRecord instanceof OAtomicUnitEndRecord
                || restoreRecord instanceof ONonTxOperationPerformedWALRecord) {
              continue;
            }

            if (restoreRecord instanceof OFileCreatedWALRecord fileCreatedCreatedRecord) {
              final String fileName =
                  fileCreatedCreatedRecord
                      .getFileName()
                      .replace("actualLocalHashTable", "expectedLocalHashTable");

              if (!expectedWriteCache.exists(fileName)) {
                expectedReadCache.addFile(
                    fileName, fileCreatedCreatedRecord.getFileId(), expectedWriteCache);
              }
            } else {
              final OUpdatePageRecord updatePageRecord = (OUpdatePageRecord) restoreRecord;

              final long fileId = updatePageRecord.getFileId();
              final long pageIndex = updatePageRecord.getPageIndex();

              if (!expectedWriteCache.exists(fileId)) {
                // some files can be absent for example configuration files
                continue;
              }

              OCacheEntry cacheEntry =
                  expectedReadCache.loadForWrite(
                      fileId, pageIndex, expectedWriteCache, false, null);
              if (cacheEntry == null) {
                do {
                  if (cacheEntry != null) {
                    expectedReadCache.releaseFromWrite(cacheEntry, expectedWriteCache, true);
                  }

                  cacheEntry = expectedReadCache.allocateNewPage(fileId, expectedWriteCache, null);
                } while (cacheEntry.getPageIndex() != pageIndex);
              }

              try {
                ODurablePage durablePage = new ODurablePage(cacheEntry);
                durablePage.restoreChanges(updatePageRecord.getChanges());
                durablePage.setLsn(new OLogSequenceNumber(0, 0));
              } finally {
                expectedReadCache.releaseFromWrite(cacheEntry, expectedWriteCache, true);
              }
            }
          }
          atomicUnit.clear();
        } else {
          Assert.assertTrue(
              "WAL record type is " + walRecord.getClass().getName(),
              walRecord instanceof OUpdatePageRecord
                  || walRecord instanceof ONonTxOperationPerformedWALRecord
                  || walRecord instanceof OFileCreatedWALRecord);
        }
      }

      walRecords = log.next(walRecords.get(walRecords.size() - 1).getLsn(), 1_000);
    }

    Assert.assertTrue(atomicUnit.isEmpty());
    log.close();
  }

  private void assertFileContentIsTheSame(
      String expectedIMCFile,
      String actualIMCFile,
      String expectedTSCFile,
      String actualTSCFile,
      String expectedNBHFile,
      String actualNBHFile,
      String expectedOBFFile,
      String actualOBFFile)
      throws IOException {

    assertFileContentIsTheSame(
        new java.io.File(expectedStorageDir, expectedIMCFile).getAbsolutePath(),
        new java.io.File(actualStorageDir, actualIMCFile).getAbsolutePath());
    assertFileContentIsTheSame(
        new java.io.File(expectedStorageDir, expectedTSCFile).getAbsolutePath(),
        new java.io.File(actualStorageDir, actualTSCFile).getAbsolutePath());
    assertFileContentIsTheSame(
        new java.io.File(expectedStorageDir, expectedNBHFile).getAbsolutePath(),
        new java.io.File(actualStorageDir, actualNBHFile).getAbsolutePath());
    assertFileContentIsTheSame(
        new java.io.File(expectedStorageDir, expectedOBFFile).getAbsolutePath(),
        new java.io.File(actualStorageDir, actualOBFFile).getAbsolutePath());
  }

  private static void assertFileContentIsTheSame(
      String expectedBTreeFileName, String actualBTreeFileName) throws IOException {
    java.io.File expectedFile = new java.io.File(expectedBTreeFileName);
    try (RandomAccessFile fileOne = new RandomAccessFile(expectedFile, "r")) {
      try (RandomAccessFile fileTwo =
          new RandomAccessFile(new java.io.File(actualBTreeFileName), "r")) {

        Assert.assertEquals(fileOne.length(), fileTwo.length());

        byte[] expectedContent = new byte[OClusterPage.PAGE_SIZE];
        byte[] actualContent = new byte[OClusterPage.PAGE_SIZE];

        fileOne.seek(OFile.HEADER_SIZE);
        fileTwo.seek(OFile.HEADER_SIZE);

        int bytesRead = fileOne.read(expectedContent);
        while (bytesRead >= 0) {
          fileTwo.readFully(actualContent, 0, bytesRead);

          Assertions.assertThat(
                  Arrays.copyOfRange(
                      expectedContent,
                      ODurablePage.NEXT_FREE_POSITION,
                      ODurablePage.MAX_PAGE_SIZE_BYTES))
              .isEqualTo(
                  Arrays.copyOfRange(
                      actualContent,
                      ODurablePage.NEXT_FREE_POSITION,
                      ODurablePage.MAX_PAGE_SIZE_BYTES));
          expectedContent = new byte[OClusterPage.PAGE_SIZE];
          actualContent = new byte[OClusterPage.PAGE_SIZE];
          bytesRead = fileOne.read(expectedContent);
        }
      }
    }
  }
}