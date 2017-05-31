/*
 * Copyright 2014 LinkedIn, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.executor;

import azkaban.jobExecutor.ProcessJob;
import azkaban.utils.Props;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

public class JavaJobRunnerMain {

  public static final String JOB_CLASS = "job.class";
  public static final String DEFAULT_RUN_METHOD = "run";
  public static final String DEFAULT_CANCEL_METHOD = "cancel";

  // This is the Job interface method to get the properties generated by the
  // job.
  public static final String GET_GENERATED_PROPERTIES_METHOD =
      "getJobGeneratedProperties";

  public static final String CANCEL_METHOD_PARAM = "method.cancel";
  public static final String RUN_METHOD_PARAM = "method.run";
  public static final String[] PROPS_CLASSES = new String[]{
      "azkaban.utils.Props", "azkaban.common.utils.Props"};

  private static final Layout DEFAULT_LAYOUT = new PatternLayout("%p %m\n");

  public final Logger _logger;

  public String _cancelMethod;
  public String _jobName;
  public Object _javaObject;
  private boolean _isFinished = false;

  public JavaJobRunnerMain() throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        cancelJob();
      }
    });

    try {
      this._jobName = System.getenv(ProcessJob.JOB_NAME_ENV);
      final String propsFile = System.getenv(ProcessJob.JOB_PROP_ENV);

      this._logger = Logger.getRootLogger();
      this._logger.removeAllAppenders();
      final ConsoleAppender appender = new ConsoleAppender(DEFAULT_LAYOUT);
      appender.activateOptions();
      this._logger.addAppender(appender);

      final Properties prop = new Properties();
      prop.load(new BufferedReader(new FileReader(propsFile)));

      this._logger.info("Running job " + this._jobName);
      final String className = prop.getProperty(JOB_CLASS);
      if (className == null) {
        throw new Exception("Class name is not set.");
      }
      this._logger.info("Class name " + className);

      // Create the object using proxy

      this._javaObject = getObject(this._jobName, className, prop, this._logger);

      if (this._javaObject == null) {
        this._logger.info("Could not create java object to run job: " + className);
        throw new Exception("Could not create running object");
      }

      this._cancelMethod =
          prop.getProperty(CANCEL_METHOD_PARAM, DEFAULT_CANCEL_METHOD);

      final String runMethod =
          prop.getProperty(RUN_METHOD_PARAM, DEFAULT_RUN_METHOD);
      this._logger.info("Invoking method " + runMethod);

      this._logger.info("Proxy check failed, not proxying run.");
      runMethod(this._javaObject, runMethod);

      this._isFinished = true;

      // Get the generated properties and store them to disk, to be read
      // by ProcessJob.
      try {
        final Method generatedPropertiesMethod =
            this._javaObject.getClass().getMethod(GET_GENERATED_PROPERTIES_METHOD,
                new Class<?>[]{});
        final Object outputGendProps =
            generatedPropertiesMethod.invoke(this._javaObject, new Object[]{});
        if (outputGendProps != null) {
          final Method toPropertiesMethod =
              outputGendProps.getClass().getMethod("toProperties",
                  new Class<?>[]{});
          final Properties properties =
              (Properties) toPropertiesMethod.invoke(outputGendProps,
                  new Object[]{});

          final Props outputProps = new Props(null, properties);
          outputGeneratedProperties(outputProps);
        } else {
          outputGeneratedProperties(new Props());
        }

      } catch (final NoSuchMethodException e) {
        this._logger
            .info(String
                .format(
                    "Apparently there isn't a method[%s] on object[%s], using empty Props object instead.",
                    GET_GENERATED_PROPERTIES_METHOD, this._javaObject));
        outputGeneratedProperties(new Props());
      }
    } catch (final Exception e) {
      this._isFinished = true;
      throw e;
    }
  }

  public static void main(final String[] args) throws Exception {
    final
    JavaJobRunnerMain wrapper = new JavaJobRunnerMain();
  }

  private static Object getObject(final String jobName, final String className,
      final Properties properties, final Logger logger) throws Exception {

    final Class<?> runningClass =
        JavaJobRunnerMain.class.getClassLoader().loadClass(className);

    if (runningClass == null) {
      throw new Exception("Class " + className
          + " was not found. Cannot run job.");
    }

    Class<?> propsClass = null;
    for (final String propClassName : PROPS_CLASSES) {
      try {
        propsClass =
            JavaJobRunnerMain.class.getClassLoader().loadClass(propClassName);
      } catch (final ClassNotFoundException e) {
      }

      if (propsClass != null
          && getConstructor(runningClass, String.class, propsClass) != null) {
        // is this the props class
        break;
      }
      propsClass = null;
    }

    Object obj = null;
    if (propsClass != null
        && getConstructor(runningClass, String.class, propsClass) != null) {
      // Create props class
      final Constructor<?> propsCon =
          getConstructor(propsClass, propsClass, Properties[].class);
      final Object props =
          propsCon.newInstance(null, new Properties[]{properties});

      final Constructor<?> con =
          getConstructor(runningClass, String.class, propsClass);
      logger.info("Constructor found " + con.toGenericString());
      obj = con.newInstance(jobName, props);
    } else if (getConstructor(runningClass, String.class, Properties.class) != null) {

      final Constructor<?> con =
          getConstructor(runningClass, String.class, Properties.class);
      logger.info("Constructor found " + con.toGenericString());
      obj = con.newInstance(jobName, properties);
    } else if (getConstructor(runningClass, String.class, Map.class) != null) {
      final Constructor<?> con =
          getConstructor(runningClass, String.class, Map.class);
      logger.info("Constructor found " + con.toGenericString());

      final HashMap<Object, Object> map = new HashMap<>();
      for (final Map.Entry<Object, Object> entry : properties.entrySet()) {
        map.put(entry.getKey(), entry.getValue());
      }
      obj = con.newInstance(jobName, map);
    } else if (getConstructor(runningClass, String.class) != null) {
      final Constructor<?> con = getConstructor(runningClass, String.class);
      logger.info("Constructor found " + con.toGenericString());
      obj = con.newInstance(jobName);
    } else if (getConstructor(runningClass) != null) {
      final Constructor<?> con = getConstructor(runningClass);
      logger.info("Constructor found " + con.toGenericString());
      obj = con.newInstance();
    } else {
      logger.error("Constructor not found. Listing available Constructors.");
      for (final Constructor<?> c : runningClass.getConstructors()) {
        logger.info(c.toGenericString());
      }
    }
    return obj;
  }

  private static Constructor<?> getConstructor(final Class<?> c, final Class<?>... args) {
    try {
      final Constructor<?> cons = c.getConstructor(args);
      return cons;
    } catch (final NoSuchMethodException e) {
      return null;
    }
  }

  private void runMethod(final Object obj, final String runMethod)
      throws IllegalAccessException, InvocationTargetException,
      NoSuchMethodException {
    obj.getClass().getMethod(runMethod, new Class<?>[]{}).invoke(obj);
  }

  private void outputGeneratedProperties(final Props outputProperties) {

    if (outputProperties == null) {
      this._logger.info("  no gend props");
      return;
    }
    for (final String key : outputProperties.getKeySet()) {
      this._logger
          .info("  gend prop " + key + " value:" + outputProperties.get(key));
    }

    final String outputFileStr = System.getenv(ProcessJob.JOB_OUTPUT_PROP_FILE);
    if (outputFileStr == null) {
      return;
    }

    this._logger.info("Outputting generated properties to " + outputFileStr);

    final Map<String, String> properties = new LinkedHashMap<>();
    for (final String key : outputProperties.getKeySet()) {
      properties.put(key, outputProperties.get(key));
    }

    OutputStream writer = null;
    try {
      writer = new BufferedOutputStream(new FileOutputStream(outputFileStr));

      // Manually serialize into JSON instead of adding org.json to
      // external classpath. Reduces one dependency for something that's
      // essentially easy.
      writer.write("{\n".getBytes());
      for (final Map.Entry<String, String> entry : properties.entrySet()) {
        writer.write(String.format("  \"%s\":\"%s\",\n",
            entry.getKey().replace("\"", "\\\\\""),
            entry.getValue().replace("\"", "\\\\\"")).getBytes());
      }
      writer.write("}".getBytes());
    } catch (final Exception e) {
      throw new RuntimeException("Unable to store output properties to: "
          + outputFileStr);
    } finally {
      try {
        if (writer != null) {
          writer.close();
        }
      } catch (final IOException e) {
      }
    }
  }

  public void cancelJob() {
    if (this._isFinished) {
      return;
    }
    this._logger.info("Attempting to call cancel on this job");
    if (this._javaObject != null) {
      Method method = null;

      try {
        method = this._javaObject.getClass().getMethod(this._cancelMethod);
      } catch (final SecurityException e) {
      } catch (final NoSuchMethodException e) {
      }

      if (method != null) {
        try {
          method.invoke(this._javaObject);
        } catch (final Exception e) {
          if (this._logger != null) {
            this._logger.error("Cancel method failed! ", e);
          }
        }
      } else {
        throw new RuntimeException("Job " + this._jobName
            + " does not have cancel method " + this._cancelMethod);
      }
    }
  }

}
