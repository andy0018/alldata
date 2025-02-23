/*
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

package org.apache.druid.query.aggregation.datasketches.tuple;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.datasketches.tuple.arrayofdoubles.ArrayOfDoublesSketch;
import org.apache.datasketches.tuple.arrayofdoubles.ArrayOfDoublesSketchIterator;
import org.apache.druid.java.util.common.IAE;
import org.apache.druid.query.aggregation.AggregatorUtil;
import org.apache.druid.query.aggregation.PostAggregator;
import org.apache.druid.query.cache.CacheKeyBuilder;
import org.apache.druid.segment.ColumnInspector;
import org.apache.druid.segment.column.ColumnType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

/**
 * Returns a list of estimate values of metrics column from a given {@link ArrayOfDoublesSketch}.
 * The result will be N double values, where N is the number of double values kept in the sketch per key.
 */
public class ArrayOfDoublesSketchToMetricsSumEstimatePostAggregator extends ArrayOfDoublesSketchUnaryPostAggregator
{

  @JsonCreator
  public ArrayOfDoublesSketchToMetricsSumEstimatePostAggregator(
      @JsonProperty("name") final String name,
      @JsonProperty("field") final PostAggregator field
  )
  {
    super(name, field);
  }

  @Override
  public double[] compute(final Map<String, Object> combinedAggregators)
  {
    final ArrayOfDoublesSketch sketch = (ArrayOfDoublesSketch) getField().compute(combinedAggregators);
    if (sketch == null) {
      return null;
    }
    final SummaryStatistics[] stats = new SummaryStatistics[sketch.getNumValues()];
    Arrays.setAll(stats, i -> new SummaryStatistics());
    final ArrayOfDoublesSketchIterator it = sketch.iterator();
    while (it.next()) {
      final double[] values = it.getValues();
      for (int i = 0; i < values.length; i++) {
        stats[i].addValue(values[i]);
      }
    }
    final double[] estimates = new double[sketch.getNumValues()];
    Arrays.setAll(estimates, i -> (stats[i].getSum()) / (sketch.getTheta()));
    return estimates;
  }

  @Override
  public ColumnType getType(ColumnInspector signature)
  {
    return ColumnType.DOUBLE_ARRAY;
  }

  @Override
  public Comparator<double[]> getComparator()
  {
    throw new IAE("Comparing arrays of estimate values is not supported");
  }

  @Override
  public byte[] getCacheKey()
  {
    return new CacheKeyBuilder(AggregatorUtil.ARRAY_OF_DOUBLES_SKETCH_TO_METRICS_SUM_ESTIMATE_CACHE_TYPE_ID)
        .appendCacheable(getField())
        .build();
  }
}
