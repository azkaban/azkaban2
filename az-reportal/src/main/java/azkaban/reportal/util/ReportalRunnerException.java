/*
 * Copyright 2012 LinkedIn Corp.
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

package azkaban.reportal.util;

public class ReportalRunnerException extends Exception {

  private static final long serialVersionUID = 1L;

  public ReportalRunnerException(final String s) {
    super(s);
  }

  public ReportalRunnerException(final Exception e) {
    super(e);
  }

  public ReportalRunnerException(final String s, final Exception e) {
    super(s, e);
  }
}
