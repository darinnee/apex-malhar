/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.datatorrent.contrib.enrich;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import com.esotericsoftware.kryo.NotNull;
import com.google.common.collect.Maps;
import com.datatorrent.lib.db.cache.CacheManager;
import com.datatorrent.lib.util.FieldInfo;

/**
 * This implementation of {@link BackendLoader} loads the data from a given file
 * into memory cache and serves queries from the cache. When this is set as
 * primaryCache in {@link CacheManager}, CacheManager can call
 * {@link #loadInitialData()} periodically to reload the file. NOTE: This loader
 * should be used with caution as all the data present in the file is loaded in
 * memory because of which the memory consumption may go up.
 *
 * @since 3.4.0
 */
@InterfaceStability.Evolving
public abstract class FSLoader extends ReadOnlyBackup
{
  @NotNull
  private String fileName;

  private transient Path filePath;
  private transient FileSystem fs;
  private transient boolean connected;

  private static final Logger logger = LoggerFactory.getLogger(FSLoader.class);

  public String getFileName()
  {
    return fileName;
  }

  public void setFileName(String fileName)
  {
    this.fileName = fileName;
  }

  @Override
  public Map<Object, Object> loadInitialData()
  {
    Map<Object, Object> result = null;
    FSDataInputStream in = null;
    BufferedReader bin = null;
    try {
      result = Maps.newHashMap();
      in = fs.open(filePath);
      bin = new BufferedReader(new InputStreamReader(in));
      String line;
      while ((line = bin.readLine()) != null) {
        try {
          Map<String, Object> tuple = extractFields(line);
          if (tuple != null && !tuple.isEmpty()) {
            result.put(getKey(tuple), getValue(tuple));
          }
        } catch (Exception parseExp) {
          logger.info("Unable to parse line {}", line);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (bin != null) {
        IOUtils.closeQuietly(bin);
      }
      if (in != null) {
        IOUtils.closeQuietly(in);
      }
      try {
        fs.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    logger.debug("loading initial data {}", result.size());
    return result;
  }

  /**
   * This method is called by {@link #loadInitialData()} to extract values from
   * a record. Concrete implementations override this method to parse a record
   * and convert it to Map of field names and values OR simply returns null to
   * skip the records.
   *
   * @param line
   *          A single record from file
   * @return a map with field name and value. Null value if returned is ignored
   */
  abstract Map<String, Object> extractFields(String line);

  private Object getValue(Map<String, Object> tuple)
  {
    ArrayList<Object> includeTuple = new ArrayList<Object>();
    for (FieldInfo s : includeFieldInfo) {
      includeTuple.add(tuple.get(s.getColumnName()));
    }
    return includeTuple;
  }

  private Object getKey(Map<String, Object> tuple)
  {
    ArrayList<Object> list = new ArrayList<Object>();
    for (FieldInfo key : lookupFieldInfo) {
      list.add(tuple.get(key.getColumnName()));
    }
    return list;
  }

  @Override
  public Object get(Object key)
  {
    return null;
  }

  @Override
  public List<Object> getAll(List<Object> keys)
  {
    return null;
  }

  @Override
  public void connect() throws IOException
  {
    Configuration conf = new Configuration();
    this.filePath = new Path(fileName);
    this.fs = FileSystem.newInstance(filePath.toUri(), conf);
    if (!fs.isFile(filePath)) {
      throw new IOException("Provided path " + fileName + " is not a file");
    }
    connected = true;
  }

  @Override
  public void disconnect() throws IOException
  {
    if (fs != null) {
      fs.close();
    }
  }

  @Override
  public boolean isConnected()
  {
    return connected;
  }
}
