/*
 * Copyright 2017 LinkedIn Corp.
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
 *
 */

package azkaban.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import azkaban.AzkabanCommonModuleConfig;
import azkaban.spi.Storage;
import azkaban.spi.StorageException;
import azkaban.spi.StorageMetadata;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.apache.commons.codec.binary.Hex;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;


public class HdfsStorage implements Storage {

  private static final Logger log = Logger.getLogger(HdfsStorage.class);
  private static final String HDFS_SCHEME = "hdfs";

  private final HdfsAuth hdfsAuth;
  private final URI rootUri;

  /**
   * NOTE!!! Any method calls using HDFS should be wrapped using a doAs call
   *
   * Possible alternative: AOP. Create a Guice injected AOP to autowrap all calls using the
   * auth code. However, all Hadoop objects need to be wrapped in that case.
   * https://github.com/google/guice/wiki/AOP
   */
  private final FileSystem hdfs;

  @Inject
  public HdfsStorage(HdfsAuth hdfsAuth, FileSystem hdfs, AzkabanCommonModuleConfig config) {
    this.hdfsAuth = requireNonNull(hdfsAuth);
    this.hdfs = requireNonNull(hdfs);

    this.rootUri = config.getHdfsRootUri();
    requireNonNull(rootUri.getAuthority(), "URI must have host:port mentioned.");
    checkArgument(HDFS_SCHEME.equals(rootUri.getScheme()));
  }

  @Override
  public InputStream get(String key) throws IOException {
    return hdfsAuth.doAs(() -> get0(key));
  }

  @VisibleForTesting
  InputStream get0(String key) throws IOException {
    return hdfs.open(new Path(rootUri.toString(), key));
  }

  @Override
  public String put(StorageMetadata metadata, File localFile) {
    return hdfsAuth.doAs(() -> put0(metadata, localFile));
  }

  @VisibleForTesting
  String put0(StorageMetadata metadata, File localFile) {
    final Path projectsPath = new Path(rootUri.getPath(), String.valueOf(metadata.getProjectId()));
    try {
      if (hdfs.mkdirs(projectsPath)) {
        log.info("Created project dir: " + projectsPath);
      }
      final Path targetPath = createTargetPath(metadata, projectsPath);
      if (hdfs.exists(targetPath)) {
        log.info(
            String.format("Duplicate Found: meta: %s path: %s", metadata, targetPath));
        return getRelativePath(targetPath);
      }

      // Copy file to HDFS
      log.info(String.format("Creating project artifact: meta: %s path: %s", metadata, targetPath));
      hdfs.copyFromLocalFile(new Path(localFile.getAbsolutePath()), targetPath);
      return getRelativePath(targetPath);
    } catch (IOException e) {
      log.error("error in put(): Metadata: " + metadata);
      throw new StorageException(e);
    }
  }

  private String getRelativePath(Path targetPath) {
    return URI.create(rootUri.getPath()).relativize(targetPath.toUri()).getPath();
  }

  private Path createTargetPath(StorageMetadata metadata, Path projectsPath) {
    return new Path(projectsPath, String.format("%s-%s.zip",
        String.valueOf(metadata.getProjectId()),
        new String(Hex.encodeHex(metadata.getHash()))
    ));
  }

  @Override
  public boolean delete(String key) {
    throw new UnsupportedOperationException("Method not implemented");
  }
}
