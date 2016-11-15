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
package org.apache.apex.malhar.lib.join;

import com.datatorrent.lib.codec.KryoSerializableStreamCodec;
import com.datatorrent.lib.util.PojoUtils;

/**
 * Stream codec based on keyExpression for POJO Inner Join Operator.
 *
 * @since 3.5.0
 */
@org.apache.hadoop.classification.InterfaceStability.Evolving
public class JoinStreamCodec extends KryoSerializableStreamCodec<Object>
{
  private transient PojoUtils.Getter<Object, Object> getter;
  private String keyExpression;

  public JoinStreamCodec(String keyExpression)
  {
    this.keyExpression = keyExpression;
  }

  @Override
  public int getPartition(Object o)
  {
    if (getter == null) {
      getter = PojoUtils.createGetter(o.getClass(), keyExpression, Object.class);
    }
    return getter.get(o).hashCode();
  }
}
