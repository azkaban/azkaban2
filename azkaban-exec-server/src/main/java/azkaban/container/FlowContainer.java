/*
 * Copyright 2019 LinkedIn Corp.
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

package azkaban.container;

import azkaban.Constants;
import azkaban.Constants.PluginManager;
import azkaban.db.DBMetrics;
import azkaban.db.DatabaseOperator;
import azkaban.db.MySQLDataSource;
import azkaban.execapp.AzkabanExecutorServer;
import azkaban.execapp.event.FlowWatcher;
import azkaban.execapp.event.RemoteFlowWatcher;
import azkaban.executor.ActiveExecutingFlowsDao;
import azkaban.executor.AssignExecutorDao;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutionFlowDao;
import azkaban.executor.ExecutionJobDao;
import azkaban.executor.ExecutionLogsDao;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutionRampDao;
import azkaban.executor.ExecutorDao;
import azkaban.executor.ExecutorEventsDao;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.FetchActiveFlowDao;
import azkaban.executor.JdbcExecutorLoader;
import azkaban.executor.NumExecutionsDao;
import azkaban.jobtype.JobTypeManager;
import azkaban.metrics.MetricsManager;
import azkaban.project.JdbcProjectImpl;
import azkaban.project.ProjectLoader;
import azkaban.utils.Props;
import com.codahale.metrics.MetricRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.log4j.Logger;

/**
 *  This class is responsible for launching a flow in a container.
 */
public class FlowContainer {

  private static final String PROJECT_DIR = "project";
  private static final String JOBTYPE_DIR = "jobtype";
  private static final String AZ_DIR = "azkaban_libs";
  //private final Path currentWorkingDir;
  private final File tokenFile;

  private static final Logger logger = Logger.getLogger(FlowContainer.class);

  // FlowRunnerManager specific code
  private final ExecutorService executorService;
  private final ExecutorLoader executorLoader;
  private final ProjectLoader projectLoader;
  private final JobTypeManager jobTypeManager;
  //private final AzkabanEventReporter azkabanEventReporter;
  private final Props azKabanProps;
  private Props globalProps;
  private final File projectDir;

  private YARNFlowRunner flowRunner;
  private Future flowFuture;

  // We want to limit the log sizes to about 20 megs
  private final String jobLogChunkSize;
  private final int jobLogNumFiles;
  // If true, jobs will validate proxy user against a list of valid proxy users.
  private final boolean validateProxyUser;

  public FlowContainer(final Path projectDirPath,
      final Path jobtypePluginPath, final Path tokenFile) throws IOException {
    this.projectDir = projectDirPath.toFile();
    this.tokenFile = tokenFile != null ? tokenFile.toFile() : null;

    // Create Azkaban Props Map
    final Map<String, String> propsMap = new HashMap<>();
    // Set db stuff.
    propsMap.put(AzkabanExecutorServer.JOBTYPE_PLUGIN_DIR,
        jobtypePluginPath.toString());
    // Add the token file
    // TODO: Figure out how to use this token file.
    propsMap.put("tokenFile", this.tokenFile != null ? this.tokenFile.toString() : null);

    this.azKabanProps = new Props(null, propsMap);
    // Setup global props if applicable
    final String globalPropsPath = this.azKabanProps.getString("executor.global.properties", null);
    if (globalPropsPath != null) {
      this.globalProps = new Props(null, globalPropsPath);
    }

    // Setup DAO, a lot of it is redundant
    final DataSource dataSource= new MySQLDataSource(this.azKabanProps,
        new DBMetrics(new MetricsManager(new MetricRegistry())));

    final DatabaseOperator dbOperator = new DatabaseOperator(
        new QueryRunner(dataSource));
    final ExecutionFlowDao executionFlowDao = new ExecutionFlowDao(dbOperator);
    final ExecutorDao executorDao = new ExecutorDao(dbOperator);
    final ExecutionJobDao executionJobDao = new ExecutionJobDao(dbOperator);
    final ExecutionLogsDao executionLogsDao = new ExecutionLogsDao(dbOperator);
    final ExecutorEventsDao executorEventsDao = new ExecutorEventsDao(dbOperator);
    final ActiveExecutingFlowsDao activeExecutingFlowsDao =
        new ActiveExecutingFlowsDao(dbOperator);
    final FetchActiveFlowDao fetchActiveFlowDao =
        new FetchActiveFlowDao(dbOperator);
    final AssignExecutorDao assignExecutorDao =
        new AssignExecutorDao(dbOperator, executorDao);
    final NumExecutionsDao numExecutionsDao = new NumExecutionsDao(dbOperator);
    final ExecutionRampDao executionRampDao = new ExecutionRampDao(dbOperator);

    // Use above objects to create Executor Loader
    this.executorLoader = new JdbcExecutorLoader(executionFlowDao, executorDao,
        executionJobDao, executionLogsDao, executorEventsDao,
        activeExecutingFlowsDao, fetchActiveFlowDao, assignExecutorDao,
        numExecutionsDao, executionRampDao);

    // project Loader
    this.projectLoader = new JdbcProjectImpl(this.azKabanProps, dbOperator);

    // setup executor service, TODO : revisit
    this.executorService = Executors.newSingleThreadExecutor();

    this.jobLogChunkSize = this.azKabanProps.getString("job.log.chunk.size", "5MB");
    this.jobLogNumFiles = this.azKabanProps.getInt("job.log.backup.index", 4);
    this.validateProxyUser = this.azKabanProps.getBoolean("proxy.user.lock.down", false);
    this.jobTypeManager =
        new JobTypeManager(
            this.azKabanProps.getString(AzkabanExecutorServer.JOBTYPE_PLUGIN_DIR,
                PluginManager.JOBTYPE_DEFAULTDIR),
            this.globalProps, getClass().getClassLoader());
  }

  public static void main(final String[] args) throws IOException, ExecutorManagerException {
    // Get all the arguments
    final String projectDir = args[0];
    // The jobtypedir is not needed for certain types.
    // it looks like "jobTypeDir=<jobTypejars.zip> else,
    // "jobTypeDir=None"
    // TODO : Add a validation logic.
    final String jobtypeDirArg = args[1];
    final String azLibDir = args[2];
    final String jobType = args[3];
    final String commonProps = args[4];
    final String commonPrivateProps = args[5];
    //final String jobPropFile = args[6];
    final int numArgs = args.length;
    String tokenFile = null;
    if (numArgs == 8) {
      // delegation token defined
      tokenFile = args[6];
    }
    final String mode = args[numArgs - 1];
    final String jobtypeDir = jobtypeDirArg.endsWith("None") ? null :
        jobtypeDirArg.substring(jobtypeDirArg.indexOf("=") + 1);

    // Setup work directories
    final Path currentWorkingDir = Paths.get("").toAbsolutePath();
    final Path projectDirPath = Paths.get(currentWorkingDir.toString(), PROJECT_DIR);
    final Path jobtypePluginPath = Paths.get(currentWorkingDir.toString(), JOBTYPE_DIR);
    Path tokenFilePath = null;

    if (mode.equals("remote")) {
      // Move files to respective dirs
      System.out.println("moving projectDir:" + projectDir + ": to " + projectDirPath);
      Files.move(Paths.get(projectDir), projectDirPath);
      // Create jobtype dir
      System.out.println("Creating jobtype dir");
      Files.createDirectories(jobtypePluginPath);
      // Common properties
      System.out.println("Moving commonProperties:" + commonProps + ": to " +
          Paths.get(jobtypePluginPath.toString(), commonProps));
      Files.move(Paths.get(commonProps),
          Paths.get(jobtypePluginPath.toString(), commonProps));
      // Common private properties
      System.out.println("Moving commonPrivate:" + commonPrivateProps +
          ": to "
          + Paths.get(jobtypePluginPath.toString(), commonPrivateProps));
      Files.move(Paths.get(commonPrivateProps),
          Paths.get(jobtypePluginPath.toString(), commonPrivateProps));
      // Move jobtype dirs
      if (jobtypeDir != null) {
        System.out.println("Moving jobtypeDir:" + jobtypeDir + ": to " +
            Paths.get(jobtypePluginPath.toString(), jobType));
        Files.move(Paths.get(jobtypeDir), Paths.get(jobtypePluginPath.toString(), jobType));
      }
      // Move Azkaban lib
      System.out.println("Moving azLibDir:" + azLibDir + ": to " +
          Paths.get(currentWorkingDir.toString(), AZ_DIR));
      Files.move(Paths.get(azLibDir), Paths.get(currentWorkingDir.toString(), AZ_DIR));

      // Delegation token
      if (tokenFile != null) {
        tokenFilePath = Paths.get(projectDirPath.toString(), tokenFile);
        System.out.println("Moving delegation token: " + tokenFile + ": to " +
            tokenFilePath);
        Files.move(Paths.get(tokenFile), tokenFilePath);
      }
    }

    // Constructor
    final FlowContainer flowContainer =
        new FlowContainer(projectDirPath, jobtypePluginPath, tokenFilePath);

    // Use some execId to execute the flow
    flowContainer.submitFlow(30000);
  }


  // Submit flow
  public void submitFlow(final int execId) throws ExecutorManagerException {
    final YARNFlowRunner flowRunner = createFlowRunner(execId);
    submitFlowRunner(flowRunner);
  }

  // create Flow Runner
  private YARNFlowRunner createFlowRunner(final int execId) throws ExecutorManagerException {
    final ExecutableFlow flow = this.executorLoader.fetchExecutableFlow(execId);
    if (flow == null) {
      throw new ExecutorManagerException("Error loading flow with exec " + execId);
    }

    // Setup flow runner
    FlowWatcher watcher = null;
    final ExecutionOptions options = flow.getExecutionOptions();
    if (options.getPipelineExecutionId() != null) {
      final int pipelinedExecId = options.getPipelineExecutionId();
      watcher = new RemoteFlowWatcher(pipelinedExecId, this.executorLoader);
    }

    final YARNFlowRunner flowRunner = new YARNFlowRunner(flow, this.executorLoader,
        this.projectLoader, this.jobTypeManager, this.azKabanProps, null,
        this.projectDir);

    flowRunner.setFlowWatcher(watcher)
        .setJobLogSettings(this.jobLogChunkSize, this.jobLogNumFiles)
        .setValidateProxyUser(this.validateProxyUser)
        .setNumJobThreads(20);

    return flowRunner;
  }

  private void submitFlowRunner(final YARNFlowRunner flowRunner) {
    // set running flow, put it in DB
    this.flowRunner = flowRunner;
    this.flowFuture = this.executorService.submit(flowRunner);
    try {
      this.flowFuture.get();
    } catch (final InterruptedException ie) {
      ie.printStackTrace();
    } catch (final ExecutionException ee) {
      ee.printStackTrace();
    }
  }
}
