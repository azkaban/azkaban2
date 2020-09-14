package azkaban.executor;

import azkaban.metrics.CommonMetrics;
import azkaban.utils.Props;
import javax.inject.Singleton;

@Singleton
public class ContainerizedExecutionManager extends ExecutorManager{

  //TODO: This class will be responsible for dispatching flows to containerized infrastructure
  //like Kubernetes. This class is created as a place holder for now.
  public ContainerizedExecutionManager(Props azkProps,
      ExecutorLoader executorLoader, CommonMetrics commonMetrics,
      ExecutorApiGateway apiGateway, RunningExecutions runningExecutions,
      ActiveExecutors activeExecutors,
      ExecutorManagerUpdaterStage updaterStage,
      ExecutionFinalizer executionFinalizer,
      RunningExecutionsUpdaterThread updaterThread) throws ExecutorManagerException {
    super(azkProps, executorLoader, commonMetrics, apiGateway, runningExecutions, activeExecutors,
        updaterStage, executionFinalizer, updaterThread);
  }
}
