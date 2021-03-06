/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.jdbc;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.test.core.VertxTestBase;
import org.jruby.RubyProcess;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:pmlopes@gmail.com">Paulo Lopes</a>
 */
public class JDBCTypesTestBase extends VertxTestBase {

  protected JDBCClient client;

  private static final List<String> SQL = new ArrayList<>();

  static {
    //TODO: Create table with more types for testing
    SQL.add("create table insert_table (id int not null primary key generated always as identity (START WITH 1, INCREMENT BY 1), lname varchar(255), fname varchar(255), dob date )");
    SQL.add("create table insert_tableNoIdentity (id int not null primary key, lname varchar(255), fname varchar(255), dob date )");
  }

  @Before
  public void setUp() throws Exception {
    super.setUp();
    client = JDBCClient.createNonShared(vertx, config());
  }

  @After
  public void after() throws Exception {
    client.close();
    super.after();
  }

  @BeforeClass
  public static void createDb() throws Exception {
    Connection conn = DriverManager.getConnection(config().getString("url"));
    for (String sql : SQL) {
      conn.createStatement().execute(sql);
    }
  }

  protected static JsonObject config() {
    return new JsonObject()
      .put("url", "jdbc:derby:memory:myDB;create=true")
      .put("driver_class", "org.apache.derby.jdbc.EmbeddedDriver");
  }

  @Test
  public void testInsertWithNullParameters() {
    SQLConnection conn = connection();
    String sql = "INSERT INTO insert_table (lname, fname, dob) VALUES (?, ?, ?)";
    JsonArray params = new JsonArray().addNull().addNull().add("2002-02-02");
    conn.updateWithParams(sql, params, onSuccess(result -> {
      assertUpdate(result, 1);
      int id = result.getKeys().getInteger(0);
      conn.queryWithParams("SElECT DOB FROM insert_table WHERE id=?", new JsonArray().add(id), onSuccess(resultSet -> {
        assertNotNull(resultSet);
        assertEquals(1, resultSet.getResults().size());

        System.out.println(resultSet.getResults().get(0).getValue(0));
        testComplete();
      }));
    }));

    await();
  }

    /**
   * Test that insert and update works in a table without an identity column.
   */
  @Test
  public void testInsertUpdateNoIdentity() {
    SQLConnection conn = connection();
    String insertsql = "INSERT INTO insert_tableNoIdentity (id, lname, fname, dob) VALUES (?, ?, ?, ?)";
    JsonArray insertparams = new JsonArray().add(1).add("LastName1").addNull().add("2002-02-02");
    conn.updateWithParams(insertsql, insertparams, onSuccess(insertResultSet -> {
      assertUpdate(insertResultSet, 1);
      int insertid = insertResultSet.getKeys().isEmpty() ? 1 : insertResultSet.getKeys().getInteger(0);
      conn.queryWithParams("SElECT lname FROM insert_tableNoIdentity WHERE id=?", new JsonArray().add(1), onSuccess(insertQueryResultSet -> {
        assertNotNull(insertQueryResultSet);
        assertEquals(1, insertQueryResultSet.getResults().size());
        assertEquals("LastName1",insertQueryResultSet.getResults().get(0).getValue(0));
        System.out.println("testInsertUpdateNoIdentity: insert: " + insertQueryResultSet.getResults().get(0).getValue(0));
        // Now test that update works
        String updSql = "UPDATE insert_tableNoIdentity SET lname=? WHERE id=?";
        JsonArray updParams = new JsonArray().add("LastName2").add(insertid);
        conn.updateWithParams(updSql, updParams, onSuccess(updateResultSet -> {
          assertUpdate(updateResultSet,1);
          int updateid = updateResultSet.getKeys().isEmpty() ? 1 : updateResultSet.getKeys().getInteger(0);
          conn.queryWithParams("SElECT lname FROM insert_tableNoIdentity WHERE id=?", new JsonArray().add(updateid), onSuccess(updateQueryResultSet -> {
            assertNotNull(updateQueryResultSet);
            assertEquals(1, updateQueryResultSet.getResults().size());
            assertEquals("LastName2",updateQueryResultSet.getResults().get(0).getValue(0));
            System.out.println("testInsertUpdateNoIdentity: update: " + updateQueryResultSet.getResults().get(0).getValue(0));
            testComplete();
          }));
        }));
      }));
    }));

    await();
  }

  private void assertUpdate(UpdateResult result, int updated) {
    assertUpdate(result, updated, false);
  }

  private void assertUpdate(UpdateResult result, int updated, boolean generatedKeys) {
    assertNotNull(result);
    assertEquals(updated, result.getUpdated());
    if (generatedKeys) {
      JsonArray keys = result.getKeys();
      assertNotNull(keys);
      assertEquals(updated, keys.size());
      Set<Integer> numbers = new HashSet<>();
      for (int i = 0; i < updated; i++) {
        assertTrue(keys.getValue(i) instanceof Integer);
        assertTrue(numbers.add(i));
      }
    }
  }

  private SQLConnection connection() {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<SQLConnection> ref = new AtomicReference<>();
    client.getConnection(onSuccess(conn -> {
      ref.set(conn);
      latch.countDown();
    }));

    try {
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    return ref.get();
  }
}
