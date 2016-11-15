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
package com.datatorrent.contrib.hbase;

import com.datatorrent.api.InputOperator;
import com.datatorrent.lib.db.AbstractStoreInputOperator;

/**
 * A base implementation of hbase input operator which derives from HBaseOperatorBase. <br>
 * <p>
 * <br>
 * @displayName HBase Input
 * @category Input
 * @tags hbase
 * @param <T> The tuple type
 * @since 0.3.2
 */
public abstract class HBaseInputOperator<T> extends AbstractStoreInputOperator<T, HBaseStore> implements InputOperator
{
}
