package com.jetbrains.youtrack.db.internal.core.storage.impl.local.paginated;

import com.jetbrains.youtrack.db.internal.DBTestBase;
import com.jetbrains.youtrack.db.internal.common.concur.lock.YTModificationOperationProhibitedException;
import com.jetbrains.youtrack.db.internal.common.io.FileUtils;
import com.jetbrains.youtrack.db.internal.common.log.LogManager;
import com.jetbrains.youtrack.db.internal.core.config.GlobalConfiguration;
import com.jetbrains.youtrack.db.internal.core.db.YTDatabaseSessionInternal;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDB;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDBConfig;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDBEmbedded;
import com.jetbrains.youtrack.db.internal.core.db.YouTrackDBInternal;
import com.jetbrains.youtrack.db.internal.core.db.tool.ODatabaseCompare;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTClass;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTSchema;
import com.jetbrains.youtrack.db.internal.core.metadata.schema.YTType;
import com.jetbrains.youtrack.db.internal.core.record.impl.EntityImpl;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;

public class StorageBackupMTIT {

  private final CountDownLatch latch = new CountDownLatch(1);
  private volatile boolean stop = false;
  private YouTrackDB youTrackDB;
  private String dbName;

  @Test
  public void testParallelBackup() throws Exception {
    final String buildDirectory = System.getProperty("buildDirectory", ".");
    dbName = StorageBackupMTIT.class.getSimpleName();
    final String dbDirectory =
        buildDirectory + File.separator + "databases" + File.separator + dbName;
    final File backupDir = new File(buildDirectory, "backupDir");
    final String backupDbName = StorageBackupMTIT.class.getSimpleName() + "BackUp";

    FileUtils.deleteRecursively(new File(dbDirectory));

    try {

      youTrackDB = new YouTrackDB(DBTestBase.embeddedDBUrl(getClass()),
          YouTrackDBConfig.defaultConfig());
      youTrackDB.execute(
          "create database " + dbName + " plocal users ( admin identified by 'admin' role admin)");

      var db = (YTDatabaseSessionInternal) youTrackDB.open(dbName, "admin", "admin");

      final YTSchema schema = db.getMetadata().getSchema();
      final YTClass backupClass = schema.createClass("BackupClass");
      backupClass.createProperty(db, "num", YTType.INTEGER);
      backupClass.createProperty(db, "data", YTType.BINARY);

      backupClass.createIndex(db, "backupIndex", YTClass.INDEX_TYPE.NOTUNIQUE, "num");

      FileUtils.deleteRecursively(backupDir);

      if (!backupDir.exists()) {
        Assert.assertTrue(backupDir.mkdirs());
      }

      final ExecutorService executor = Executors.newCachedThreadPool();
      final List<Future<Void>> futures = new ArrayList<>();

      for (int i = 0; i < 4; i++) {
        futures.add(executor.submit(new DataWriterCallable()));
      }

      futures.add(executor.submit(new DBBackupCallable(backupDir.getAbsolutePath())));

      latch.countDown();

      TimeUnit.MINUTES.sleep(15);

      stop = true;

      for (Future<Void> future : futures) {
        future.get();
      }

      System.out.println("do inc backup last time");
      db.incrementalBackup(backupDir.getAbsolutePath());

      youTrackDB.close();

      final String backedUpDbDirectory = buildDirectory + File.separator + backupDbName;
      FileUtils.deleteRecursively(new File(backedUpDbDirectory));

      System.out.println("create and restore");

      YouTrackDBEmbedded embedded =
          (YouTrackDBEmbedded)
              YouTrackDBInternal.embedded(buildDirectory, YouTrackDBConfig.defaultConfig());
      embedded.restore(
          backupDbName,
          null,
          null,
          null,
          backupDir.getAbsolutePath(),
          YouTrackDBConfig.defaultConfig());
      embedded.close();

      youTrackDB = new YouTrackDB(DBTestBase.embeddedDBUrl(getClass()),
          YouTrackDBConfig.defaultConfig());
      final ODatabaseCompare compare =
          new ODatabaseCompare(
              (YTDatabaseSessionInternal) youTrackDB.open(dbName, "admin", "admin"),
              (YTDatabaseSessionInternal) youTrackDB.open(backupDbName, "admin", "admin"),
              System.out::println);

      System.out.println("compare");
      boolean areSame = compare.compare();
      Assert.assertTrue(areSame);

    } finally {
      if (youTrackDB != null && youTrackDB.isOpen()) {
        try {
          youTrackDB.close();
        } catch (Exception ex) {
          LogManager.instance().error(this, "", ex);
        }
      }
      try {
        youTrackDB = new YouTrackDB(DBTestBase.embeddedDBUrl(getClass()),
            YouTrackDBConfig.defaultConfig());
        youTrackDB.drop(dbName);
        youTrackDB.drop(backupDbName);

        youTrackDB.close();

        FileUtils.deleteRecursively(backupDir);
      } catch (Exception ex) {
        LogManager.instance().error(this, "", ex);
      }
    }
  }

  @Test
  public void testParallelBackupEncryption() throws Exception {
    final String buildDirectory = System.getProperty("buildDirectory", ".");
    final String backupDbName = StorageBackupMTIT.class.getSimpleName() + "BackUp";
    final String backedUpDbDirectory = buildDirectory + File.separator + backupDbName;
    final File backupDir = new File(buildDirectory, "backupDir");

    dbName = StorageBackupMTIT.class.getSimpleName();
    String dbDirectory = buildDirectory + File.separator + dbName;

    final YouTrackDBConfig config =
        YouTrackDBConfig.builder()
            .addConfig(GlobalConfiguration.STORAGE_ENCRYPTION_KEY, "T1JJRU5UREJfSVNfQ09PTA==")
            .build();

    try {

      FileUtils.deleteRecursively(new File(dbDirectory));

      youTrackDB = new YouTrackDB(DBTestBase.embeddedDBUrl(getClass()), config);
      youTrackDB.execute(
          "create database " + dbName + " plocal users ( admin identified by 'admin' role admin)");

      var db = (YTDatabaseSessionInternal) youTrackDB.open(dbName, "admin", "admin");

      final YTSchema schema = db.getMetadata().getSchema();
      final YTClass backupClass = schema.createClass("BackupClass");
      backupClass.createProperty(db, "num", YTType.INTEGER);
      backupClass.createProperty(db, "data", YTType.BINARY);

      backupClass.createIndex(db, "backupIndex", YTClass.INDEX_TYPE.NOTUNIQUE, "num");

      FileUtils.deleteRecursively(backupDir);

      if (!backupDir.exists()) {
        Assert.assertTrue(backupDir.mkdirs());
      }

      final ExecutorService executor = Executors.newCachedThreadPool();
      final List<Future<Void>> futures = new ArrayList<>();

      for (int i = 0; i < 4; i++) {
        futures.add(executor.submit(new DataWriterCallable()));
      }

      futures.add(executor.submit(new DBBackupCallable(backupDir.getAbsolutePath())));

      latch.countDown();

      TimeUnit.MINUTES.sleep(5);

      stop = true;

      for (Future<Void> future : futures) {
        future.get();
      }

      System.out.println("do inc backup last time");
      db.incrementalBackup(backupDir.getAbsolutePath());

      youTrackDB.close();

      FileUtils.deleteRecursively(new File(backedUpDbDirectory));

      System.out.println("create and restore");

      YouTrackDBEmbedded embedded =
          (YouTrackDBEmbedded) YouTrackDBInternal.embedded(buildDirectory, config);
      embedded.restore(backupDbName, null, null, null, backupDir.getAbsolutePath(), config);
      embedded.close();

      GlobalConfiguration.STORAGE_ENCRYPTION_KEY.setValue("T1JJRU5UREJfSVNfQ09PTA==");
      youTrackDB = new YouTrackDB(DBTestBase.embeddedDBUrl(getClass()),
          YouTrackDBConfig.defaultConfig());
      final ODatabaseCompare compare =
          new ODatabaseCompare(
              (YTDatabaseSessionInternal) youTrackDB.open(dbName, "admin", "admin"),
              (YTDatabaseSessionInternal) youTrackDB.open(backupDbName, "admin", "admin"),
              System.out::println);
      System.out.println("compare");

      boolean areSame = compare.compare();
      Assert.assertTrue(areSame);

    } finally {
      if (youTrackDB != null && youTrackDB.isOpen()) {
        try {
          youTrackDB.close();
        } catch (Exception ex) {
          LogManager.instance().error(this, "", ex);
        }
      }
      try {
        youTrackDB = new YouTrackDB(DBTestBase.embeddedDBUrl(getClass()), config);
        youTrackDB.drop(dbName);
        youTrackDB.drop(backupDbName);

        youTrackDB.close();

        FileUtils.deleteRecursively(backupDir);
      } catch (Exception ex) {
        LogManager.instance().error(this, "", ex);
      }
    }
  }

  private final class DataWriterCallable implements Callable<Void> {

    @Override
    public Void call() throws Exception {
      latch.await();

      System.out.println(Thread.currentThread() + " - start writing");

      try (var ignored = youTrackDB.open(dbName, "admin", "admin")) {
        final Random random = new Random();
        while (!stop) {
          try {
            final byte[] data = new byte[16];
            random.nextBytes(data);

            final int num = random.nextInt();

            final EntityImpl document = new EntityImpl("BackupClass");
            document.field("num", num);
            document.field("data", data);

            document.save();
          } catch (YTModificationOperationProhibitedException e) {
            System.out.println("Modification prohibited ... wait ...");
            //noinspection BusyWait
            Thread.sleep(1000);
          } catch (Exception | Error e) {
            LogManager.instance().error(this, "", e);
            throw e;
          }
        }
      }

      System.out.println(Thread.currentThread() + " - done writing");

      return null;
    }
  }

  public final class DBBackupCallable implements Callable<Void> {

    private final String backupPath;

    public DBBackupCallable(String backupPath) {
      this.backupPath = backupPath;
    }

    @Override
    public Void call() throws Exception {
      latch.await();

      try (var db = youTrackDB.open(dbName, "admin", "admin")) {
        System.out.println(Thread.currentThread() + " - start backup");
        while (!stop) {
          TimeUnit.MINUTES.sleep(1);

          System.out.println(Thread.currentThread() + " do inc backup");
          db.incrementalBackup(backupPath);
          System.out.println(Thread.currentThread() + " done inc backup");
        }
      } catch (Exception | Error e) {
        LogManager.instance().error(this, "", e);
        throw e;
      }

      System.out.println(Thread.currentThread() + " - stop backup");

      return null;
    }
  }
}