/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.frontend;

import com.google.cloud.tools.jib.configuration.LayerConfiguration;
import com.google.cloud.tools.jib.image.LayerEntry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Builds {@link LayerConfiguration}s for a Java application. */
public class JavaLayerConfigurations {
  private static final String WEBAPP_LIB = "/WEB-INF/lib";
  private static final String WEBAPP_CLASSES = "/WEB-INF/classes";

  /** Represents the different types of layers for a Java application. */
  @VisibleForTesting
  enum LayerType {
    DEPENDENCIES(
        "dependencies", JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE, WEBAPP_LIB),
    SNAPSHOT_DEPENDENCIES(
        "snapshot dependencies",
        JavaEntrypointConstructor.DEFAULT_DEPENDENCIES_PATH_ON_IMAGE,
        WEBAPP_LIB),
    RESOURCES(
        "resources", JavaEntrypointConstructor.DEFAULT_RESOURCES_PATH_ON_IMAGE, WEBAPP_CLASSES),
    META_INF("meta-inf", "/app/META-INF", "/META-INF"),
    WEB_INF("web-inf", "/app/WEB-INF", "/WEB-INF"),
    WEB_APP("webapp", "/app", "/"),
    CLASSES("classes", JavaEntrypointConstructor.DEFAULT_CLASSES_PATH_ON_IMAGE, WEBAPP_CLASSES),
    EXTRA_FILES("extra files", "/", null); // not relative to the webapp root

    private final String label;
    private final String extractionPath;
    @Nullable private final String webAppExtractionRelativePath;

    /** Initializes with a label for the layer and the layer files' default extraction path root. */
    LayerType(String label, String extractionPath, @Nullable final String webappExtractionPath) {
      this.label = label;
      this.extractionPath = extractionPath;
      this.webAppExtractionRelativePath = webappExtractionPath;
    }

    @VisibleForTesting
    String getLabel() {
      return label;
    }

    @VisibleForTesting
    String getExtractionPath() {
      return extractionPath;
    }

    @VisibleForTesting
    @Nullable
    String getWebAppExtractionRelativePath() {
      return webAppExtractionRelativePath;
    }
  }

  /** Builds with each layer's files. */
  public static class Builder {

    private final Map<LayerType, List<Path>> layerFilesMap = new EnumMap<>(LayerType.class);
    private @Nullable String webAppRoot = null;

    private Builder() {
      for (LayerType layerType : LayerType.values()) {
        layerFilesMap.put(layerType, new ArrayList<>());
      }
    }

    public Builder setDependenciesFiles(List<Path> dependenciesFiles) {
      layerFilesMap.put(LayerType.DEPENDENCIES, dependenciesFiles);
      return this;
    }

    public Builder setSnapshotDependenciesFiles(List<Path> snapshotDependenciesFiles) {
      layerFilesMap.put(LayerType.SNAPSHOT_DEPENDENCIES, snapshotDependenciesFiles);
      return this;
    }

    public Builder setResourcesFiles(List<Path> resourcesFiles) {
      layerFilesMap.put(LayerType.RESOURCES, resourcesFiles);
      return this;
    }

    public Builder setClassesFiles(List<Path> classesFiles) {
      layerFilesMap.put(LayerType.CLASSES, classesFiles);
      return this;
    }

    public Builder setExtraFiles(List<Path> extraFiles) {
      layerFilesMap.put(LayerType.EXTRA_FILES, extraFiles);
      return this;
    }

    public Builder setWebAppFiles(List<Path> webAppFiles) {
      layerFilesMap.put(LayerType.WEB_APP, webAppFiles);
      return this;
    }

    public Builder setMetaInfFiles(List<Path> metaInfFiles) {
      layerFilesMap.put(LayerType.META_INF, metaInfFiles);
      return this;
    }

    public Builder setWebInfFiles(List<Path> webInfFiles) {
      layerFilesMap.put(LayerType.WEB_INF, webInfFiles);
      return this;
    }

    public Builder setWebAppRoot(@Nullable final String webAppRoot) {
      this.webAppRoot = webAppRoot;
      return this;
    }

    public JavaLayerConfigurations build() {
      ImmutableMap.Builder<LayerType, LayerConfiguration> layerConfigurationsMap =
          ImmutableMap.builderWithExpectedSize(LayerType.values().length);
      for (LayerType layerType : LayerType.values()) {
        List<Path> layerFiles = Preconditions.checkNotNull(layerFilesMap.get(layerType));
        layerConfigurationsMap.put(
            layerType,
            LayerConfiguration.builder()
                .addEntry(
                    layerFiles,
                    this.webAppRoot != null && layerType.getWebAppExtractionRelativePath() != null
                        ? webAppRoot + layerType.getWebAppExtractionRelativePath()
                        : layerType.getExtractionPath())
                .setLabel(layerType.getLabel())
                .build());
      }
      return new JavaLayerConfigurations(layerConfigurationsMap.build());
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final ImmutableMap<LayerType, LayerConfiguration> layerConfigurationMap;

  private JavaLayerConfigurations(
      ImmutableMap<LayerType, LayerConfiguration> layerConfigurationsMap) {
    this.layerConfigurationMap = layerConfigurationsMap;
  }

  public ImmutableList<LayerConfiguration> getLayerConfigurations() {
    return layerConfigurationMap.values().asList();
  }

  public LayerEntry getDependenciesLayerEntry() {
    return getLayerEntry(LayerType.DEPENDENCIES);
  }

  public LayerEntry getSnapshotDependenciesLayerEntry() {
    return getLayerEntry(LayerType.SNAPSHOT_DEPENDENCIES);
  }

  public LayerEntry getResourcesLayerEntry() {
    return getLayerEntry(LayerType.RESOURCES);
  }

  public LayerEntry getClassesLayerEntry() {
    return getLayerEntry(LayerType.CLASSES);
  }

  public LayerEntry getExtraFilesLayerEntry() {
    return getLayerEntry(LayerType.EXTRA_FILES);
  }

  public LayerEntry getWebAppFilesLayerEntry() {
    return getLayerEntry(LayerType.WEB_APP);
  }

  public LayerEntry getWebInfFilesLayerEntry() {
    return getLayerEntry(LayerType.WEB_INF);
  }

  public LayerEntry getMetaInfFilesLayerEntry() {
    return getLayerEntry(LayerType.META_INF);
  }

  private LayerEntry getLayerEntry(LayerType layerType) {
    return Preconditions.checkNotNull(layerConfigurationMap.get(layerType))
        .getLayerEntries()
        .get(0);
  }
}
