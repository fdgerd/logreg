package com.andrehacker.ml.datasets;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Provides information about Donut dataset, as used in Mahout in action
 * 
 */
public class DonutDatasetInfo {

  private static final int TOTAL = 40;
  
  private static List<String> predictorNames = Lists.newArrayList(new String[] {
      "x", "y", "shape", "xx", "xy", "yy", "a", "b", "c"
   });
  
  private static final Map<Integer, String> labelMap = ImmutableMap.of(
      0, "color"
      );

  private static DatasetInfo datasetInfo = new DatasetInfo(predictorNames, labelMap, TOTAL);
  
  public static DatasetInfo get() {
    return datasetInfo;
  }
  
}
