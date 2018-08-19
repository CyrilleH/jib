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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.War;

/** Builds {@link JavaLayerConfigurations} based on inputs from a {@link Project}. */
class GradleLayerConfigurations {

  /** Name of the `main` {@link SourceSet} to use as source files. */
  private static final String MAIN_SOURCE_SET_NAME = "main";

  /**
   * Resolves the source files configuration for a Gradle {@link Project}.
   *
   * @param project the Gradle {@link Project}
   * @param gradleJibLogger the build logger for providing feedback about the resolution
   * @param extraDirectory path to the directory for the extra files layer
   * @param webAppRoot container path for the exploded WAR file
   * @return a {@link JavaLayerConfigurations} for the layers for the Gradle {@link Project}
   * @throws IOException if an I/O exception occurred during resolution
   */
  static JavaLayerConfigurations getForProject(
      Project project,
      GradleJibLogger gradleJibLogger,
      Path extraDirectory,
      @Nullable String webAppRoot,
      @Nullable Path metaInfDirectory,
      @Nullable Path webInfDirectory)
      throws IOException {
    JavaPluginConvention javaPluginConvention =
        project.getConvention().getPlugin(JavaPluginConvention.class);

    SourceSet mainSourceSet = javaPluginConvention.getSourceSets().getByName(MAIN_SOURCE_SET_NAME);

    List<Path> dependenciesFiles = new ArrayList<>();
    List<Path> snapshotDependenciesFiles = new ArrayList<>();
    List<Path> resourcesFiles = new ArrayList<>();
    List<Path> classesFiles = new ArrayList<>();
    List<Path> extraFiles = new ArrayList<>();
    List<Path> webAppFiles = new ArrayList<>();
    List<Path> metaInfFiles = new ArrayList<>();
    List<Path> webInfFiles = new ArrayList<>();

    // Adds each file in the resources output directory to the resources files list.
    Path resourcesOutputDirectory = mainSourceSet.getOutput().getResourcesDir().toPath();
    if (Files.exists(resourcesOutputDirectory)) {
      try (Stream<Path> resourceFileStream = Files.list(resourcesOutputDirectory)) {
        resourceFileStream.forEach(resourcesFiles::add);
      }
    }

    FileCollection allFiles;
    FileCollection classesOutputDirectories;

    final WarPluginConvention warPluginConvention =
        project.getConvention().findPlugin(WarPluginConvention.class);
    if (warPluginConvention != null) {
      War war = (War) warPluginConvention.getProject().getTasks().findByName("war");
      final Path webappOutputDirectory = warPluginConvention.getWebAppDir().toPath();
      if (Files.exists(webappOutputDirectory)) {
        try (Stream<Path> stream = Files.list(webappOutputDirectory)) {
          stream.forEach(webAppFiles::add);
        }
      }
      allFiles = war.getClasspath();
      classesOutputDirectories = war.getClasspath().filter(File::isDirectory);
    } else {
      allFiles = mainSourceSet.getRuntimeClasspath();
      classesOutputDirectories = mainSourceSet.getOutput().getClassesDirs();
    }

    // Adds each file in each classes output directory to the classes files list.
    gradleJibLogger.info("Adding corresponding output directories of source sets to image");
    for (File classesOutputDirectory : classesOutputDirectories) {
      if (Files.notExists(classesOutputDirectory.toPath())) {
        gradleJibLogger.info("\t'" + classesOutputDirectory + "' (not found, skipped)");
        continue;
      }
      if (!resourcesOutputDirectory.equals(classesOutputDirectory.toPath())) {
        gradleJibLogger.info("\t'" + classesOutputDirectory + "'");
        try (Stream<Path> classFileStream = Files.list(classesOutputDirectory.toPath())) {
          classFileStream.forEach(classesFiles::add);
        }
      }
    }
    if (classesFiles.isEmpty()) {
      gradleJibLogger.warn("No classes files were found - did you compile your project?");
    }

    // Adds all other files to the dependencies files list.
    // Removes the classes output directories.
    allFiles = allFiles.minus(classesOutputDirectories);
    for (File dependencyFile : allFiles) {
      // Removes the resources output directory.
      if (resourcesOutputDirectory.equals(dependencyFile.toPath())) {
        continue;
      }
      if (dependencyFile.getName().contains("SNAPSHOT")) {
        snapshotDependenciesFiles.add(dependencyFile.toPath());
      } else {
        dependenciesFiles.add(dependencyFile.toPath());
      }
    }

    // Adds all the extra files.
    if (Files.exists(extraDirectory)) {
      try (Stream<Path> extraFilesLayerDirectoryFiles = Files.list(extraDirectory)) {
        extraFiles = extraFilesLayerDirectoryFiles.collect(Collectors.toList());
      }
    }

    // Adds all the META-INF files.
    if (metaInfDirectory != null && Files.exists(metaInfDirectory)) {
      try (Stream<Path> s = Files.list(metaInfDirectory)) {
        metaInfFiles = s.collect(Collectors.toList());
      }
    }

    // Adds all the WEB-INF files.
    if (webInfDirectory != null && Files.exists(webInfDirectory)) {
      try (Stream<Path> s = Files.list(webInfDirectory)) {
        webInfFiles = s.collect(Collectors.toList());
      }
    }

    // Sorts all files by path for consistent ordering.
    Collections.sort(dependenciesFiles);
    Collections.sort(snapshotDependenciesFiles);
    Collections.sort(resourcesFiles);
    Collections.sort(classesFiles);
    Collections.sort(extraFiles);
    Collections.sort(webAppFiles);
    Collections.sort(webInfFiles);
    Collections.sort(metaInfFiles);

    return JavaLayerConfigurations.builder()
        .setDependenciesFiles(dependenciesFiles)
        .setSnapshotDependenciesFiles(snapshotDependenciesFiles)
        .setResourcesFiles(resourcesFiles)
        .setClassesFiles(classesFiles)
        .setWebAppFiles(webAppFiles)
        .setMetaInfFiles(metaInfFiles)
        .setWebInfFiles(webInfFiles)
        .setWebAppRoot(webAppRoot)
        .setExtraFiles(extraFiles)
        .build();
  }

  private GradleLayerConfigurations() {}
}
