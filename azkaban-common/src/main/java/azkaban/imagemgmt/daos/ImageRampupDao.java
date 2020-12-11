/*
 * Copyright 2020 LinkedIn Corp.
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
package azkaban.imagemgmt.daos;

import azkaban.imagemgmt.exeception.ImageMgmtException;
import azkaban.imagemgmt.models.ImageRampup;
import azkaban.imagemgmt.models.ImageRampupPlan;
import azkaban.imagemgmt.models.ImageRampupPlanRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Data access object (DAO) for accessing image rampup plan and rampups for an image type. This
 * interface defines method such as create, get, update etc. for image rampups.
 */
public interface ImageRampupDao {

  /**
   * Creates image version metadata for an image type.
   *
   * @param imageRampupPlanRequest
   * @return int - id of the image version
   */
  public int createImageRampupPlan(ImageRampupPlanRequest imageRampupPlanRequest)
      throws ImageMgmtException;

  /**
   * Gets active rampup plan for the image type.
   *
   * @param imageTypeName - Name of the image type
   * @param fetchRampup   - If true fetches rampup plan along with the rampup for the image type. If
   *                      false, fetches only the rampup plan for the image type
   * @return Optional<ImageRampupPlan>
   * @throws ImageMgmtException
   */
  public Optional<ImageRampupPlan> getActiveImageRampupPlan(String imageTypeName,
      boolean fetchRampup) throws ImageMgmtException;

  /**
   * Fetches active rampup details for all the available active image types.
   *
   * @return Map<String, List < ImageRampup>>
   * @throws ImageMgmtException
   */
  public Map<String, List<ImageRampup>> fetchRampupForAllImageTypes()
      throws ImageMgmtException;

  /**
   * Fetches rampup details for all the given image types.
   *
   * @param imageTypes
   * @return Map<String, List < ImageRampup>>
   * @throws ImageMgmtException
   */
  public Map<String, List<ImageRampup>> fetchRampupByImageTypes(Set<String> imageTypes)
      throws ImageMgmtException;

  /**
   * Update image rampup for an image type.
   *
   * @param imageRampupPlanRequest
   * @return int - id of the image version
   */
  public void updateImageRampupPlan(ImageRampupPlanRequest imageRampupPlanRequest)
      throws ImageMgmtException;
}
