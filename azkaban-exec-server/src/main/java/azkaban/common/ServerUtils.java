/*
 * Copyright 2021 LinkedIn Corp.
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


package azkaban.common;

import azkaban.Constants;
import azkaban.execapp.event.JobCallbackManager;
import azkaban.utils.Props;
import org.apache.log4j.Logger;

/**
 * Common utility methods for Azkaban Executor and Flow Container
 */
public class ServerUtils {

  /**
   * Method to initialize jobcallback manager if it is enabled.
   * @param logger : the logger object of calling class.
   * @param props : Azkaban properties
   */
  public static void configureJobCallback(final Logger logger, final Props props) {
    final boolean jobCallbackEnabled =
            props.getBoolean(Constants.ConfigurationKeys.AZKABAN_EXECUTOR_JOBCALLBACK_ENABLED, true);

    logger.info("Job callback enabled? " + jobCallbackEnabled);

    if (jobCallbackEnabled) {
      JobCallbackManager.initialize(props);
    }
  }
}
