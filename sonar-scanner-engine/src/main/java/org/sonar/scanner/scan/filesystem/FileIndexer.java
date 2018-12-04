/*
 * SonarQube
 * Copyright (C) 2009-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.scanner.scan.filesystem;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.InputFileFilter;
import org.sonar.api.batch.fs.internal.DefaultIndexedFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.batch.fs.internal.DefaultInputProject;
import org.sonar.api.batch.fs.internal.SensorStrategy;
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.scanner.issue.ignore.scanner.IssueExclusionsLoader;
import org.sonar.scanner.scan.ScanProperties;
import org.sonar.scanner.util.ProgressReport;

/**
 * Index input files into {@link InputComponentStore}.
 */
public class FileIndexer {

  private static final Logger LOG = Loggers.get(FileIndexer.class);
  private final AnalysisWarnings analysisWarnings;
  private final ScanProperties properties;
  private final InputFileFilter[] filters;
  private final ProjectExclusionFilters projectExclusionFilters;
  private final ProjectCoverageExclusions projectCoverageExclusions;
  private final IssueExclusionsLoader issueExclusionsLoader;
  private final MetadataGenerator metadataGenerator;
  private final DefaultInputProject project;
  private final ScannerComponentIdGenerator scannerComponentIdGenerator;
  private final InputComponentStore componentStore;
  private final SensorStrategy sensorStrategy;
  private final LanguageDetection langDetection;

  private boolean warnExclusionsAlreadyLogged;
  private boolean warnCoverageExclusionsAlreadyLogged;

  public FileIndexer(DefaultInputProject project, ScannerComponentIdGenerator scannerComponentIdGenerator, InputComponentStore componentStore,
    ProjectExclusionFilters projectExclusionFilters, ProjectCoverageExclusions projectCoverageExclusions, IssueExclusionsLoader issueExclusionsLoader,
    MetadataGenerator metadataGenerator, SensorStrategy sensorStrategy, LanguageDetection languageDetection, AnalysisWarnings analysisWarnings, ScanProperties properties,
    InputFileFilter[] filters) {
    this.project = project;
    this.scannerComponentIdGenerator = scannerComponentIdGenerator;
    this.componentStore = componentStore;
    this.projectCoverageExclusions = projectCoverageExclusions;
    this.issueExclusionsLoader = issueExclusionsLoader;
    this.metadataGenerator = metadataGenerator;
    this.sensorStrategy = sensorStrategy;
    this.langDetection = languageDetection;
    this.analysisWarnings = analysisWarnings;
    this.properties = properties;
    this.filters = filters;
    this.projectExclusionFilters = projectExclusionFilters;
  }

  public FileIndexer(DefaultInputProject project, ScannerComponentIdGenerator scannerComponentIdGenerator, InputComponentStore componentStore,
    ProjectExclusionFilters projectExclusionFilters, ProjectCoverageExclusions projectCoverageExclusions, IssueExclusionsLoader issueExclusionsLoader,
    MetadataGenerator metadataGenerator, SensorStrategy sensorStrategy, LanguageDetection languageDetection, AnalysisWarnings analysisWarnings, ScanProperties properties) {
    this(project, scannerComponentIdGenerator, componentStore, projectExclusionFilters, projectCoverageExclusions, issueExclusionsLoader, metadataGenerator, sensorStrategy,
      languageDetection,
      analysisWarnings,
      properties, new InputFileFilter[0]);
  }

  public void indexFile(DefaultInputModule module, ModuleExclusionFilters moduleExclusionFilters, ModuleCoverageExclusions moduleCoverageExclusions, Path sourceFile,
    InputFile.Type type, ProgressReport progressReport,
    AtomicInteger excludedByPatternsCount)
    throws IOException {
    // get case of real file without resolving link
    Path realAbsoluteFile = sourceFile.toRealPath(LinkOption.NOFOLLOW_LINKS).toAbsolutePath().normalize();
    if (!realAbsoluteFile.startsWith(project.getBaseDir())) {
      LOG.warn("File '{}' is ignored. It is not located in project basedir '{}'.", realAbsoluteFile.toAbsolutePath(), project.getBaseDir());
      return;
    }
    Path projectRelativePath = project.getBaseDir().relativize(realAbsoluteFile);
    Path moduleRelativePath = module.getBaseDir().relativize(realAbsoluteFile);
    if (!projectExclusionFilters.accept(realAbsoluteFile, projectRelativePath, type)) {
      excludedByPatternsCount.incrementAndGet();
      return;
    }
    if (!moduleExclusionFilters.accept(realAbsoluteFile, moduleRelativePath, type)) {
      if (projectExclusionFilters.equals(moduleExclusionFilters)) {
        warnOnceDeprecatedExclusion("File '" + projectRelativePath + "' was excluded because patterns are still evaluated using module relative paths but this is deprecated. " +
          "Please update file inclusion/exclusion configuration so that patterns refer to project relative paths.");
      }
      excludedByPatternsCount.incrementAndGet();
      return;
    }
    String language = langDetection.language(realAbsoluteFile, projectRelativePath);
    if (language == null && langDetection.getForcedLanguage() != null) {
      LOG.warn("File '{}' is ignored because it doesn't belong to the forced language '{}'", realAbsoluteFile.toAbsolutePath(), langDetection.getForcedLanguage());
      return;
    }
    DefaultIndexedFile indexedFile = new DefaultIndexedFile(realAbsoluteFile, project.key(),
      projectRelativePath.toString(),
      moduleRelativePath.toString(),
      type, language, scannerComponentIdGenerator.getAsInt(), sensorStrategy);
    DefaultInputFile inputFile = new DefaultInputFile(indexedFile, f -> metadataGenerator.setMetadata(module.getKeyWithBranch(), f, module.getEncoding()));
    if (language != null) {
      inputFile.setPublished(true);
    }
    if (!accept(inputFile)) {
      return;
    }
    checkIfAlreadyIndexed(inputFile);
    componentStore.put(module.key(), inputFile);
    if (issueExclusionsLoader.shouldExecute()) {
      issueExclusionsLoader.addMulticriteriaPatterns(inputFile.getProjectRelativePath(), inputFile.key());
    }
    LOG.debug("'{}' indexed {}with language '{}'", projectRelativePath, type == Type.TEST ? "as test " : "", inputFile.language());
    evaluateCoverageExclusions(module, moduleCoverageExclusions, inputFile);
    if (properties.preloadFileMetadata()) {
      inputFile.checkMetadata();
    }
    int count = componentStore.inputFiles().size();
    progressReport.message(count + " " + pluralizeFiles(count) + " indexed...  (last one was " + inputFile.getProjectRelativePath() + ")");
  }

  private void checkIfAlreadyIndexed(DefaultInputFile inputFile) {
    if (componentStore.inputFile(inputFile.getProjectRelativePath()) != null) {
      throw MessageException.of("File " + inputFile + " can't be indexed twice. Please check that inclusion/exclusion patterns produce "
        + "disjoint sets for main and test files");
    }
  }

  private void evaluateCoverageExclusions(DefaultInputModule module, ModuleCoverageExclusions moduleCoverageExclusions, DefaultInputFile inputFile) {
    boolean excludedByProjectConfiguration = projectCoverageExclusions.isExcluded(inputFile);
    if (excludedByProjectConfiguration) {
      inputFile.setExcludedForCoverage(true);
      LOG.debug("File {} excluded for coverage", inputFile);
    } else if (moduleCoverageExclusions.isExcluded(inputFile)) {
      inputFile.setExcludedForCoverage(true);
      if (Arrays.equals(moduleCoverageExclusions.getCoverageExclusionConfig(), projectCoverageExclusions.getCoverageExclusionConfig())) {
        warnOnceDeprecatedCoverageExclusion(
          "File '" + inputFile + "' was excluded from coverage because patterns are still evaluated using module relative paths but this is deprecated. " +
            "Please update '" + CoreProperties.PROJECT_COVERAGE_EXCLUSIONS_PROPERTY + "' configuration so that patterns refer to project relative paths");
      }
      LOG.debug("File {} excluded for coverage", inputFile);
    }
  }

  private void warnOnceDeprecatedExclusion(String msg) {
    if (!warnExclusionsAlreadyLogged) {
      LOG.warn(msg);
      analysisWarnings.addUnique(msg);
      warnExclusionsAlreadyLogged = true;
    }
  }

  private void warnOnceDeprecatedCoverageExclusion(String msg) {
    if (!warnCoverageExclusionsAlreadyLogged) {
      LOG.warn(msg);
      analysisWarnings.addUnique(msg);
      warnCoverageExclusionsAlreadyLogged = true;
    }
  }

  private boolean accept(InputFile indexedFile) {
    // InputFileFilter extensions. Might trigger generation of metadata
    for (InputFileFilter filter : filters) {
      if (!filter.accept(indexedFile)) {
        LOG.debug("'{}' excluded by {}", indexedFile, filter.getClass().getName());
        return false;
      }
    }
    return true;
  }

  private static String pluralizeFiles(int count) {
    return count == 1 ? "file" : "files";
  }

}
