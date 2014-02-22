/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.contrib.adsdimension;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;
import java.util.Collection;
import java.util.Map.Entry;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.map.hash.TCustomHashMap;
import gnu.trove.strategy.HashingStrategy;

import com.datatorrent.api.*;
import com.datatorrent.api.Context.OperatorContext;
import com.datatorrent.api.annotation.ShipContainingJars;

/**
 *
 * @param <EVENT> - Type of the tuple whose attributes are used to define dimensions.
 */
@ShipContainingJars(classes = {TCustomHashMap.class, HashingStrategy.class})
public class DimensionsComputation<EVENT> implements Operator, Partitionable<DimensionsComputation<EVENT>>
{
  public final transient DefaultOutputPort<EVENT> output = new DefaultOutputPort<EVENT>();
  public final transient DefaultInputPort<EVENT> data = new DefaultInputPort<EVENT>()
  {
    @Override
    public void process(EVENT tuple)
    {
      for (AggregatorMap<EVENT> dimension : aggregatorMaps) {
        dimension.add(tuple);
      }
    }

  };

  public static interface Aggregator<EVENT> extends HashingStrategy<EVENT>
  {
    EVENT getGroup(EVENT src);

    void aggregate(EVENT dest, EVENT src);

  }

  private Aggregator<EVENT>[] aggregators;
  private transient AggregatorMap<EVENT>[] aggregatorMaps;

  /**
   * Set the dimensions which should each get the tuples going forward.
   * A dimension is specified by the colon separated list of fields names which together forms dimension.
   * Dimesions are separated by comma. This form is chosen so that dimensions can be controlled through
   * properties file as well.
   *
   * @param specs
   */
  public void setAggregators(Aggregator<EVENT>[] specs)
  {
    aggregators = specs.clone();
  }

  public Aggregator<EVENT>[] getAggregators()
  {
    return aggregators.clone();
  }

  @Override
  public void beginWindow(long windowId)
  {
  }

  @Override
  public void endWindow()
  {
    for (AggregatorMap<EVENT> dimension : aggregatorMaps) {
      for (EVENT value : dimension.values()) {
        output.emit(value);
      }
      dimension.clear();
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void setup(OperatorContext context)
  {
    aggregatorMaps = (AggregatorMap[])Array.newInstance(AggregatorMap.class, aggregators.length);
    int i = 0;
    for (Aggregator<EVENT> aggregator : aggregators) {
      aggregatorMaps[i++] = new AggregatorMap<EVENT>(aggregator);
    }
  }

  @Override
  public void teardown()
  {
  }

  @Override
  public Collection<Partition<DimensionsComputation<EVENT>>> definePartitions(Collection<Partition<DimensionsComputation<EVENT>>> partitions, int incrementalCapacity)
  {
    if (incrementalCapacity == 0) {
      return partitions;
    }

    int newPartitionsCount = partitions.size() + incrementalCapacity;

    LinkedHashMap<Aggregator<EVENT>, DimensionsComputation<EVENT>> map = new LinkedHashMap<Aggregator<EVENT>, DimensionsComputation<EVENT>>(newPartitionsCount);
    for (Partition<DimensionsComputation<EVENT>> partition : partitions) {
      for (Aggregator<EVENT> dimension : partition.getPartitionedInstance().getAggregators()) {
        map.put(dimension, partition.getPartitionedInstance());
      }
    }

    int remainingDimensions = map.size();
    if (newPartitionsCount > remainingDimensions) {
      newPartitionsCount = remainingDimensions;
    }

    int dimensionsPerPartition[] = new int[newPartitionsCount];
    while (remainingDimensions > 0) {
      for (int i = 0; i < newPartitionsCount && remainingDimensions > 0; i++) {
        dimensionsPerPartition[i]++;
        remainingDimensions--;
      }
    }

    ArrayList<Partition<DimensionsComputation<EVENT>>> returnValue = new ArrayList<Partition<DimensionsComputation<EVENT>>>(newPartitionsCount);

    Iterator<Entry<Aggregator<EVENT>, DimensionsComputation<EVENT>>> iterator = map.entrySet().iterator();
    for (int i = 0; i < newPartitionsCount; i++) {
      DimensionsComputation<EVENT> dc = new DimensionsComputation<EVENT>();
      for (int j = 0; j < dimensionsPerPartition[i]; j++) {
        Entry<Aggregator<EVENT>, DimensionsComputation<EVENT>> next = iterator.next();
        dc.transferDimension(next.getKey(), next.getValue());
      }

      DefaultPartition<DimensionsComputation<EVENT>> partition = new DefaultPartition<DimensionsComputation<EVENT>>(dc);
      returnValue.add(partition);
    }

    return returnValue;
  }

  public void transferDimension(Aggregator<EVENT> aggregator, DimensionsComputation<EVENT> other)
  {
    if (other.aggregatorMaps == null) {
      if (this.aggregatorMaps == null) {
        @SuppressWarnings("unchecked")
        AggregatorMap<EVENT>[] newInstance = (AggregatorMap<EVENT>[])Array.newInstance(AggregatorMap.class, 1);
        this.aggregatorMaps = newInstance;
      }
      else {
        this.aggregatorMaps = Arrays.copyOf(this.aggregatorMaps, this.aggregatorMaps.length + 1);
      }

      this.aggregatorMaps[this.aggregatorMaps.length - 1] = new AggregatorMap<EVENT>(aggregator);
    }
    else {
      int i = other.aggregatorMaps.length;
      while (i-- > 0) {
        AggregatorMap<EVENT> otherMap = other.aggregatorMaps[i];
        if (aggregator.equals(otherMap.aggregator)) {
          other.aggregatorMaps[i] = null;
          @SuppressWarnings("unchecked")
          AggregatorMap<EVENT>[] newArray = (AggregatorMap<EVENT>[])Array.newInstance(AggregatorMap.class, other.aggregatorMaps.length - 1);

          i = 0;
          for (AggregatorMap<EVENT> dimesion : other.aggregatorMaps) {
            if (dimesion != null) {
              newArray[i++] = dimesion;
            }
          }
          other.aggregatorMaps = newArray;


          if (this.aggregatorMaps == null) {
            @SuppressWarnings("unchecked")
            AggregatorMap<EVENT>[] newInstance = (AggregatorMap<EVENT>[])Array.newInstance(AggregatorMap.class, 1);
            this.aggregatorMaps = newInstance;
          }
          else {
            this.aggregatorMaps = Arrays.copyOf(this.aggregatorMaps, this.aggregatorMaps.length + 1);
          }

          this.aggregatorMaps[this.aggregatorMaps.length - 1] = otherMap;
          break;
        }
      }
    }
  }

  /**
   * Kryo has an issue in prior to version 2.23 where it does not honor
   * KryoSerializer implementation.
   *
   * So we provide serializer in a different way. This code will not be
   * needed after 2.23 is released.
   *
   * @param <T>
   */
  public static class ExternalizableSerializer<T extends Externalizable> extends Serializer<T>
  {
    @Override
    public void write(Kryo kryo, Output output, T object)
    {
      try {
        ObjectOutputStream stream;
        object.writeExternal(stream = new ObjectOutputStream(output));
        stream.flush();
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    @Override
    public T read(Kryo kryo, Input input, Class<T> type)
    {
      T object = kryo.newInstance(type);
      kryo.reference(object);
      try {
        object.readExternal(new ObjectInputStream(input));
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
      return object;
    }

  }

  @DefaultSerializer(ExternalizableSerializer.class)
  static class AggregatorMap<EVENT> extends TCustomHashMap<EVENT, EVENT>
  {
    Aggregator<EVENT> aggregator;

    @SuppressWarnings("PublicConstructorInNonPublicClass")
    public AggregatorMap()
    {
      /* Needed for Serialization */
      super();
      aggregator = null;
    }

    AggregatorMap(Aggregator<EVENT> aggregator)
    {
      super(aggregator);
      this.aggregator = aggregator;
    }

    AggregatorMap(Aggregator<EVENT> aggregator, int initialCapacity)
    {
      super(aggregator, initialCapacity);
      this.aggregator = aggregator;
    }

    public void add(EVENT tuple)
    {
      EVENT event = get(tuple);
      if (event == null) {
        event = aggregator.getGroup(tuple);
        put(event, event);
      }

      aggregator.aggregate(event, tuple);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
      super.readExternal(in);
      aggregator = (Aggregator<EVENT>)super.strategy;
    }

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(AggregatorMap.class);
    private static final long serialVersionUID = 201311171410L;
  }

}
