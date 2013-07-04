package com.andrehacker.ml.logreg.sfo;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.SequenceFile;

import com.andrehacker.ml.logreg.sfo.SFODriver.FeatureGain;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;

public class SFOTools {
  
  /**
   * Makes the current base model available to mappers/reducers via hdfs
   * 
   * We use hdfs (and not distributed cache) because the base model remains
   * the same for train and test job.
   * 
   * TODO Improvement: Appending only every single added dimension would be nice
   * to reduce startup costs
   */
  static void writeBaseModel(IncrementalModel baseModel) throws IOException {
    Configuration conf = new Configuration();
    conf.addResource(new Path(GlobalJobSettings.CONFIG_FILE_PATH));
    
    FileSystem fs = FileSystem.get(conf);
    SequenceFile.Writer writer = null;
    try {
      writer = SequenceFile.createWriter(fs, conf, new Path(GlobalJobSettings.BASE_MODEL_PATH),
          IncrementalModelWritable.class, NullWritable.class);
      writer.append(new IncrementalModelWritable(baseModel), NullWritable.get());
    } finally {
      Closeables.close(writer, true);
    }
  }
    
  static IncrementalModel readBaseModel(Configuration conf) throws IOException {
    IncrementalModel baseModel = null;
    FileSystem fs = FileSystem.get(conf);
    SequenceFile.Reader reader = new SequenceFile.Reader(fs, new Path(GlobalJobSettings.BASE_MODEL_PATH), conf);
    try {
      IncrementalModelWritable baseModelWritable = new IncrementalModelWritable();
      // model is stored in key
      reader.next(baseModelWritable);
      baseModel = baseModelWritable.getModel();
    } finally {
      Closeables.close(reader, true);
    }
    
    return baseModel;
  }
  
  static List<FeatureGain> readEvalResult(String path) throws IOException {
    
    List<FeatureGain> list = Lists.newArrayList();
    
    Configuration conf = new Configuration();
    conf.addResource(new Path(GlobalJobSettings.CONFIG_FILE_PATH));
    
    Path dir = new Path(path);
    FileSystem fs = FileSystem.get(conf);
    FileStatus[] statusList = fs.listStatus(dir, new PathFilter() {
      @Override
      public boolean accept(Path path) {
        if (path.getName().startsWith("part-r")) return true;
        else return false;
      }
    });
//    System.out.println("Read gain from " + statusList.length + " files");
    for (FileStatus status : statusList) {
      SequenceFile.Reader reader = new SequenceFile.Reader(fs, status.getPath(), conf);
      try {
        IntWritable dimension = new IntWritable();
        DoubleWritable gain = new DoubleWritable();
        while (reader.next(dimension, gain)) {
          list.add(new FeatureGain(dimension.get(), gain.get()));
        }
      } finally {
        Closeables.close(reader, true);
      }
    }
    
    return list;
  }
  
  static List<Double> readTrainedCoefficients(Configuration conf) throws IOException {
    
    // Read trained coefficients into map: dimension -> coefficient
    List<Double> coefficients = Arrays.asList(new Double[(int)GlobalJobSettings.datasetInfo.getNumFeatures()+1]);
    
    Path dir = new Path(SFOJobTest.TRAIN_OUTPUT_PATH);
    FileSystem fs = FileSystem.get(conf);
    FileStatus[] statusList = fs.listStatus(dir, new PathFilter() {
      @Override
      public boolean accept(Path path) {
        if (path.getName().startsWith("part-r")) return true;
        else return false;
      }
    });
//    System.out.println("Read trained coefficients from " + statusList.length + " files");
    for (FileStatus status : statusList) {
      SequenceFile.Reader reader = new SequenceFile.Reader(fs, status.getPath(), conf);
      try {
        IntWritable dimension = new IntWritable();
        DoubleWritable coefficient = new DoubleWritable();
        while (reader.next(dimension, coefficient)) {
          coefficients.set(dimension.get(), coefficient.get());
//          System.out.println(dimension.get() + ": " + coefficient.get());
        }
      } finally {
        Closeables.close(reader, true);
      }
    }
    return coefficients;
  }

}
