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
package org.apache.apex.malhar.lib.dimensions.aggregator;

import org.apache.apex.malhar.lib.dimensions.DimensionsEvent.Aggregate;
import org.apache.apex.malhar.lib.dimensions.DimensionsEvent.InputEvent;

import com.datatorrent.api.annotation.Name;
import com.datatorrent.lib.appdata.gpo.GPOUtils;
import com.datatorrent.lib.appdata.schemas.FieldsDescriptor;
import com.datatorrent.lib.appdata.schemas.Type;

/**
 * <p>
 * This aggregator creates an aggregate out of the first {@link InputEvent} encountered by this aggregator. All
 * subsequent
 * {@link InputEvent}s are ignored.
 * </p>
 * <p>
 * <b>Note:</b> when aggregates are combined in a unifier it is not possible to tell which came first or last, so
 * one is picked arbitrarily to be the first.
 * </p>
 *
 * @since 3.1.0
 */
@Name("FIRST")
public class AggregatorFirst extends AbstractIncrementalAggregator
{
  private static final long serialVersionUID = 20154301646L;

  public AggregatorFirst()
  {
    //Do nothing
  }

  @Override
  public Aggregate getGroup(InputEvent src, int aggregatorIndex)
  {
    Aggregate aggregate = super.getGroup(src, aggregatorIndex);

    GPOUtils.indirectCopy(aggregate.getAggregates(), src.getAggregates(), context.indexSubsetAggregates);

    return aggregate;
  }

  @Override
  public Type getOutputType(Type inputType)
  {
    return AggregatorUtils.IDENTITY_TYPE_MAP.get(inputType);
  }

  @Override
  public void aggregate(Aggregate dest, InputEvent src)
  {
    //Ignore
  }

  @Override
  public void aggregate(Aggregate dest, Aggregate src)
  {
    //Ignore
  }

  @Override
  public FieldsDescriptor getMetaDataDescriptor()
  {
    return null;
  }
}
