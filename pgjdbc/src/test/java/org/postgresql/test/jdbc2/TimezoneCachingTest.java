/*-------------------------------------------------------------------------
 *
 * Copyright (c) 2003-2016, PostgreSQL Global Development Group
 *
 *
 *-------------------------------------------------------------------------
 */

package org.postgresql.test.jdbc2;

import org.postgresql.PGProperty;
import org.postgresql.test.TestUtil;

import java.lang.reflect.Field;
import java.sql.BatchUpdateException;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.TimeZone;

public class TimezoneCachingTest extends BaseTest {

  /**
   * Test to check the internal cached timezone of a prepared statement is set/cleared as expected.
   */
  public void testPreparedStatementCachedTimezoneInstance() throws SQLException {
    Timestamp ts = new Timestamp(2016 - 1900, 1 - 1, 31, 0, 0, 0, 0);
    Date date = new Date(2016 - 1900, 1 - 1, 31);
    Time time = new Time(System.currentTimeMillis());
    TimeZone tz = TimeZone.getDefault();
    PreparedStatement pstmt = null;
    try {
      pstmt = con.prepareStatement("INSERT INTO testtz VALUES (?,?)");
      assertEquals(
          "Cache never initialized: must be null",
          getTimeZoneCache(pstmt), null);
      pstmt.setInt(1, 1);
      assertEquals(
          "Cache never initialized: must be null",
          getTimeZoneCache(pstmt), null);
      pstmt.setTimestamp(2, ts);
      assertEquals(
          "Cache initialized by setTimestamp(xx): must not be null",
          getTimeZoneCache(pstmt), tz);
      pstmt.addBatch();
      assertEquals(
          "Cache was initialized, addBatch does not change that: must not be null",
          getTimeZoneCache(pstmt), tz);
      pstmt.setInt(1, 2);
      pstmt.setNull(2, java.sql.Types.DATE);
      assertEquals(
          "Cache was initialized, setNull does not change that: must not be null",
          getTimeZoneCache(pstmt), tz);
      pstmt.addBatch();
      assertEquals(
          "Cache was initialized, addBatch does not change that: must not be null",
          getTimeZoneCache(pstmt), tz);
      pstmt.executeBatch();
      assertEquals(
          "Cache reset by executeBatch(): must be null",
          getTimeZoneCache(pstmt), null);
      pstmt.setInt(1, 3);
      assertEquals(
          "Cache not initialized: must be null",
          getTimeZoneCache(pstmt), null);
      pstmt.setInt(1, 4);
      pstmt.setNull(2, java.sql.Types.DATE);
      assertEquals(
          "Cache was not initialized, setNull does not change that: must be null",
          getTimeZoneCache(pstmt), null);
      pstmt.setTimestamp(2, ts);
      assertEquals(
          "Cache initialized by setTimestamp(xx): must not be null",
          getTimeZoneCache(pstmt), tz);
      pstmt.clearParameters();
      assertEquals(
          "Cache was initialized, clearParameters does not change that: must not be null",
          getTimeZoneCache(pstmt), tz);
      pstmt.setInt(1, 5);
      pstmt.setTimestamp(2, ts);
      pstmt.addBatch();
      pstmt.executeBatch();
      pstmt.close();
      pstmt = con.prepareStatement("UPDATE testtz SET col2 = ? WHERE col1 = 1");
      assertEquals(
          "Cache not initialized: must be null",
          getTimeZoneCache(pstmt), null);
      pstmt.setDate(1, date);
      assertEquals(
          "Cache initialized by setDate(xx): must not be null",
          getTimeZoneCache(pstmt), tz);
      pstmt.execute();
      assertEquals(
          "Cache reset by execute(): must be null",
          getTimeZoneCache(pstmt), null);
      pstmt.setDate(1, date);
      assertEquals(
          "Cache initialized by setDate(xx): must not be null",
          getTimeZoneCache(pstmt), tz);
      pstmt.executeUpdate();
      assertEquals(
          "Cache reset by executeUpdate(): must be null",
          getTimeZoneCache(pstmt), null);
      pstmt.setTime(1, time);
      assertEquals(
          "Cache initialized by setTime(xx): must not be null",
          getTimeZoneCache(pstmt), tz);
      pstmt.close();
      pstmt = con.prepareStatement("SELECT * FROM testtz WHERE col2 = ?");
      pstmt.setDate(1, date);
      assertEquals(
          "Cache initialized by setDate(xx): must not be null",
          getTimeZoneCache(pstmt), tz);
      pstmt.executeQuery();
      assertEquals(
          "Cache reset by executeQuery(): must be null",
          getTimeZoneCache(pstmt), null);
    } finally {
      TestUtil.closeQuietly(pstmt);
    }
  }

  /**
   * Test to check the internal cached timezone of a prepared statement is used as expected.
   */
  public void testPreparedStatementCachedTimezoneUsage() throws SQLException {
    Timestamp ts = new Timestamp(2016 - 1900, 1 - 1, 31, 0, 0, 0, 0);
    Statement stmt = null;
    PreparedStatement pstmt = null;
    TimeZone tz1 = TimeZone.getTimeZone("GMT+8:00");
    TimeZone tz2 = TimeZone.getTimeZone("GMT-2:00");
    TimeZone tz3 = TimeZone.getTimeZone("UTC+2");
    TimeZone tz4 = TimeZone.getTimeZone("UTC+3");
    Calendar c3 = new GregorianCalendar(tz3);
    Calendar c4 = new GregorianCalendar(tz4);
    try {
      stmt = con.createStatement();
      TimeZone.setDefault(tz1);
      pstmt = con.prepareStatement("INSERT INTO testtz VALUES(1, ?)");
      pstmt.setTimestamp(1, ts);
      pstmt.executeUpdate();
      checkTimestamp("Default is tz2, was saved as tz1, expecting tz1", stmt, ts, tz1);
      pstmt.close();
      pstmt = con.prepareStatement("UPDATE testtz SET col2 = ? WHERE col1 = ?");
      pstmt.setTimestamp(1, ts);
      TimeZone.setDefault(tz2);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.executeBatch();
      checkTimestamp("Default is tz2, but was saved as tz1, expecting tz1", stmt, ts, tz1);
      pstmt.setTimestamp(1, ts);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.executeBatch();
      checkTimestamp("Default is tz2, was saved as tz2, expecting tz2", stmt, ts, tz2);
      pstmt.setTimestamp(1, ts);
      pstmt.setInt(2, 1);
      pstmt.clearParameters();
      TimeZone.setDefault(tz1);
      pstmt.setTimestamp(1, ts);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.executeBatch();
      checkTimestamp(
          "Default is tz1, but was first saved as tz2, next save used tz2 cache, expecting tz2",
          stmt, ts, tz2);
      pstmt.setTimestamp(1, ts, c3);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.executeBatch();
      checkTimestamp("Explicit use of tz3, expecting tz3", stmt, ts, tz3);
      pstmt.setTimestamp(1, ts, c3);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.setTimestamp(1, ts, c4);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.executeBatch();
      checkTimestamp("Last set explicitly used tz4, expecting tz4", stmt, ts, tz4);
      pstmt.setTimestamp(1, ts, c3);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.setTimestamp(1, ts);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.setTimestamp(1, ts, c4);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.executeBatch();
      checkTimestamp("Last set explicitly used tz4, expecting tz4", stmt, ts, tz4);
      pstmt.setTimestamp(1, ts, c3);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.setTimestamp(1, ts);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.executeBatch();
      checkTimestamp(
          "Default is tz1, was first saved as tz1, last save used tz1 cache, expecting tz1", stmt,
          ts, tz1);
      pstmt.setTimestamp(1, ts);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.setTimestamp(1, ts, c4);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.setTimestamp(1, ts);
      pstmt.setInt(2, 1);
      pstmt.addBatch();
      pstmt.executeBatch();
      checkTimestamp(
          "Default is tz1, was first saved as tz1, last save used tz1 cache, expecting tz1", stmt,
          ts, tz1);
    } catch (BatchUpdateException ex) {
      SQLException nextException = ex.getNextException();
      nextException.printStackTrace();
    } finally {
      TimeZone.setDefault(null);
      TestUtil.closeQuietly(pstmt);
      TestUtil.closeQuietly(stmt);
    }
  }

  /**
   * Test to check the internal cached timezone of a result set is set/cleared as expected.
   */
  public void testResultSetCachedTimezoneInstance() throws SQLException {
    Timestamp ts = new Timestamp(2016 - 1900, 1 - 1, 31, 0, 0, 0, 0);
    TimeZone tz = TimeZone.getDefault();
    Statement stmt = null;
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
      pstmt = con.prepareStatement("INSERT INTO testtz VALUES (?,?)");
      pstmt.setInt(1, 1);
      pstmt.setTimestamp(2, ts);
      pstmt.addBatch();
      pstmt.executeBatch();
      stmt = con.createStatement();
      rs = stmt.executeQuery("SELECT col1, col2 FROM testtz");
      rs.next();
      assertEquals("Cache never initialized: must be null", getTimeZoneCache(rs), null);
      rs.getInt(1);
      assertEquals("Cache never initialized: must be null", getTimeZoneCache(rs), null);
      rs.getTimestamp(2);
      assertEquals("Cache initialized by getTimestamp(x): must not be null",
          getTimeZoneCache(rs), tz);
      rs.close();
      rs = stmt.executeQuery("SELECT col1, col2 FROM testtz");
      rs.next();
      rs.getInt(1);
      assertEquals("Cache never initialized: must be null", getTimeZoneCache(rs), null);
      rs.getObject(2);
      assertEquals("Cache initialized by getObject(x) on a DATE column: must not be null",
          getTimeZoneCache(rs), tz);
      rs.close();
      rs = stmt.executeQuery("SELECT col1, col2 FROM testtz");
      rs.next();
      assertEquals("Cache should NOT be set", getTimeZoneCache(rs), null);
      rs.getInt(1);
      assertEquals("Cache never initialized: must be null", getTimeZoneCache(rs), null);
      rs.getDate(2);
      assertEquals("Cache initialized by getDate(x): must not be null", getTimeZoneCache(rs), tz);
      rs.close();
    } finally {
      TestUtil.closeQuietly(rs);
      TestUtil.closeQuietly(pstmt);
      TestUtil.closeQuietly(stmt);
    }
  }

  /**
   * Test to check the internal cached timezone of a result set is used as expected.
   */
  public void testResultSetCachedTimezoneUsage() throws SQLException {
    Timestamp ts = new Timestamp(2016 - 1900, 1 - 1, 31, 0, 0, 0, 0);
    Statement stmt = null;
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    TimeZone tz1 = TimeZone.getTimeZone("GMT+8:00");
    TimeZone tz2 = TimeZone.getTimeZone("GMT-2:00");
    Calendar c1 = new GregorianCalendar(tz1);
    Calendar c2 = new GregorianCalendar(tz2);
    try {
      TimeZone.setDefault(tz1);
      pstmt = con.prepareStatement("INSERT INTO testtz VALUES (?,?)");
      pstmt.setInt(1, 1);
      // We are in tz1, so timestamp added as tz1.
      pstmt.setTimestamp(2, ts);
      pstmt.addBatch();
      pstmt.executeBatch();
      stmt = con.createStatement();
      rs = stmt.executeQuery("SELECT col1, col2 FROM testtz");
      rs.next();
      rs.getInt(1);
      assertTrue(
          "Current TZ is tz1, empty cache to be initialized to tz1 => retrieve in tz1, timestamps must be equal",
          rs.getTimestamp(2).equals(ts));
      rs.close();
      rs = stmt.executeQuery("SELECT col1, col2 FROM testtz");
      rs.next();
      rs.getInt(1);
      TimeZone.setDefault(tz2);
      assertTrue(
          "Current TZ is tz2, empty cache to be initialized to tz2 => retrieve in tz2, timestamps cannot be equal",
          !rs.getTimestamp(2).equals(ts));
      assertTrue(
          "Explicit tz1 calendar, so timestamps must be equal",
          rs.getTimestamp(2, c1).equals(ts));
      assertTrue(
          "Cache was initialized to tz2, so timestamps cannot be equal",
          !rs.getTimestamp(2).equals(ts));
      TimeZone.setDefault(tz1);
      assertTrue(
          "Cache was initialized to tz2, so timestamps cannot be equal",
          !rs.getTimestamp(2).equals(ts));
      rs.close();
      rs = stmt.executeQuery("SELECT col1, col2 FROM testtz");
      rs.next();
      rs.getInt(1);
      assertTrue(
          "Explicit tz2 calendar, so timestamps cannot be equal",
          !rs.getTimestamp(2, c2).equals(ts));
      assertTrue(
          "Current TZ is tz1, empty cache to be initialized to tz1 => retrieve in tz1, timestamps must be equal",
          rs.getTimestamp(2).equals(ts));
      assertTrue(
          "Explicit tz2 calendar, so timestamps cannot be equal",
          !rs.getTimestamp(2, c2).equals(ts));
      assertTrue(
          "Explicit tz2 calendar, so timestamps must be equal",
          rs.getTimestamp(2, c1).equals(ts));
      rs.close();
    } finally {
      TimeZone.setDefault(null);
      TestUtil.closeQuietly(rs);
      TestUtil.closeQuietly(pstmt);
      TestUtil.closeQuietly(stmt);
    }
  }

  private void checkTimestamp(String checkText, Statement stmt, Timestamp ts, TimeZone tz)
      throws SQLException {
    TimeZone prevTz = TimeZone.getDefault();
    TimeZone.setDefault(tz);
    ResultSet rs = stmt.executeQuery("SELECT col2 FROM testtz");
    rs.next();
    Timestamp dbTs = rs.getTimestamp(1);
    rs.close();
    TimeZone.setDefault(prevTz);
    assertEquals(checkText, dbTs, ts);
  }

  private TimeZone getTimeZoneCache(Object stmt) {
    try {
      Field defaultTimeZoneField = stmt.getClass().getDeclaredField("defaultTimeZone");
      defaultTimeZoneField.setAccessible(true);
      return (TimeZone) defaultTimeZoneField.get(stmt);
    } catch (Exception e) {
    }
    return null;
  }

  public TimezoneCachingTest(String name) {
    super(name);
    try {
      Class.forName("org.postgresql.Driver");
    } catch (Exception ex) {
    }
  }

  /* Set up the fixture for this test case: a connection to a database with
  a table for this test. */
  protected void setUp() throws Exception {
    super.setUp();
    Statement stmt = con.createStatement();

    /* Drop the test table if it already exists for some reason. It is
    not an error if it doesn't exist. */
    TestUtil.createTable(con, "testtz", "col1 INTEGER, col2 TIMESTAMP");

    stmt.close();

    /* Generally recommended with batch updates. By default we run all
    tests in this test case with autoCommit disabled. */
    con.setAutoCommit(false);
  }

  // Tear down the fixture for this test case.
  protected void tearDown() throws SQLException {
    con.setAutoCommit(true);

    TestUtil.dropTable(con, "testtz");
    super.tearDown();
  }

  @Override
  protected void updateProperties(Properties props) {
    props.setProperty(PGProperty.REWRITE_BATCHED_INSERTS.getName(),
        Boolean.TRUE.toString());
    props.setProperty(PGProperty.PREPARE_THRESHOLD.getName(), "1");
  }
}
