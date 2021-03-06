package de.tuberlin.dima.experiments

import scala.sys.process._
import java.util.Properties
import java.io.FileInputStream
import java.io.File
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import scala.collection.mutable.StringBuilder

/**
 * Abstract class for a system under test such as Hadoop or Stratosphere.
 * The system under test is assumed to be based on hdfs.
 * Wraps functionality for deployment, running and logging
 * 
 * Convention: All directory have no trailing /. E.g. /path/to/config-dir 
 * 
 * TODO Avoid redundant specification of properties (in sys-conf file and conf-templates).
 *      Read properties from conf-templates directly (not possible for PID folder, defined in .sh file)
 * TODO Copy java properties file to experiment log folder
 * TODO Minor: We should add methods fsStart and fsStop.
 *             So we can restart the hdfs after every execution of the experiment to always use the cold-cache-mode 
 */
abstract class SUT(confFile: String) {
  
  private val logger = LoggerFactory.getLogger(this.getClass())
  
  // ---------- PRIMARY CONSTRUCTOR ----------
  
  val prop = new Properties()
  prop.load(new FileInputStream(confFile))
  
  // ---------- ABSTRACT METHODS ----------
  
  /**
   * Deploy the system under test such as hadoop or stratosphere
   * May throw exception in case of problems (makes no sense to continue)
   */
  def deploy()
  
  /**
   * Adapt the number of slaves for the whole system under test (e.g. hadoop and stratosphere).
   */
  def adaptSlaves(numSlaves: Int)
  
  /**
   * Format the underlying filesystem, start it and wait 
   * until all slaves have connected (datanodes for hdfs) 
   */
  def fsFormatStartWait(numSlaves: Int)
  
  /**
   * Remove all files from the filesystem and stop it.
   * May not return before the system stopped.
   * You must call stop() before to stop the system on top of the filesystem
   */
  def fsCleanStop()
  
  /**
   * Load a single file or directory from local filesystem to the distributed filesystem 
   */
  def fsLoadData(localPath: String, destinationPath: String): Boolean
  
  /**
   * Start the system under test
   * and wait until all nodes (e.g. tasktrackers) have connected.
   * fsFormatStartWait must be called before.
   */
  def startWait(numSlaves: Int)
  
  /**
   * Stop the system under test.
   * Convention: Call this before calling fsCleanStop()
   */
  def stop()
  
  /**
   * Remove output from a previous job (e.g. hadoop, ozone).
   * Does nothing if the folder does not exists.
   */
  def removeOutputFolder(outputPath: String): Boolean
  
  /**
   * Create a backup of the files of a single job (e.g. one hadoop mapred job).
   * Files will be copied to experiment-log-folder/experimentID/logName/.
   * You have to call this multiple times if your experiment is a chained job,
   * because every job has it's own output directory
   * 
   * TODO What about the backup of the job results/output?
   */
  def backupJobLogs(outputPath: String, experimentID: String, logName: String): Boolean
  
  
  // ---------- BASE IMPLEMENTATIONS ----------
  
  def bash(str: String, logOutput: Boolean = true) = {
    logger.info("- exec (bash): " + str)
    var out = new StringBuilder
    var err = new StringBuilder
    // Use ProcessLogger to catch the results of stdout and strerr
    // Use bash to enable the use of bash features (e.g. wildcards)
    val exitcode = Process("/bin/bash", Seq("-c", str)) ! ProcessLogger(
        (s) => out.append(s+"\n"),
        (s) => err.append(s+"\n"));
    if (logOutput) {
      if (!out.toString.trim.isEmpty) {
        logger.info(" - result stdout: " + out) }
      if (!err.toString.trim.isEmpty) {
        logger.info(" - result strerr: " + err) }
    }
    (out.toString, err.toString, exitcode)
  }
  
  def getProperty(name: String): String = {
    val value = prop.getProperty(name)
    if (value != null) {
      logger.info("Loaded Property: " + name + " = " + value)
      value
    } else throw new RuntimeException("Could not read property " + name)
  }

  def getOptionalProperty(name: String, default: String): String = {
    prop.getProperty(name, default)
  }

  def requirePathExists(path: String) = {
    // Check if this path exists
    if (! (new File(path).exists()))
      throw new RuntimeException("Required file or directory does not exist: " + path);
  }
  
  /**
   * Deploy a SUT from a tar file.
   * Logic is the same for multiple systems like Hadoop or Ozone, so we can reuse it
   */
  protected def deployFromTar(tarPath: String, systemHome: String, confTemplatePath: String, confPath: String, user: String, group: String) = {

    requirePathExists(tarPath)
    requirePathExists(confTemplatePath)

    logger.info("- Removing old SUT home folder")
    bash("rm -Rf " + systemHome)
    
    val systemHomeParent = systemHome.substring(0, systemHome.lastIndexOf("/"))
    if (!(new File(systemHomeParent)).exists()) {
      (new File(systemHomeParent)).mkdir()
    }
    logger.info("- Unpacking tar")
    bash("tar -xzvf " + tarPath + " -C " + systemHomeParent, false)
    
    logger.info("Copy config files from " + confTemplatePath + " to " + confPath)
    (new File(confPath)).mkdirs()  // create folder if it does not yet exist (the case for yarn config folder)
    FileUtils.copyDirectory(new File(confTemplatePath), new File(confPath));
    
    // Grants write permissions to group for all files and directories (by default only for some) 
    logger.info("- Setting proper rights in SUT home")
    bash("chown -R " + user + ":" + group + " " + systemHome)
    bash("find " + systemHome + " -type f | xargs -I{} chmod g+w {}")
    bash("find " + systemHome + " -type d | xargs -I{} chmod g+w {}")
//    p("find " + systemHome + " -type f") #| p("xargs -I{} chmod g+w {}") !;
//    p("find " + systemHome + " -type d") #| p("xargs -I{} chmod g+w {}") !;
  }

}