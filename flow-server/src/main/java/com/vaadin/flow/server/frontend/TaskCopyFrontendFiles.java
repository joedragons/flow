/*
 * Copyright 2000-2022 Vaadin Ltd.
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
package com.vaadin.flow.server.frontend;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.vaadin.flow.server.Constants.COMPATIBILITY_RESOURCES_FRONTEND_DEFAULT;
import static com.vaadin.flow.server.Constants.RESOURCES_FRONTEND_DEFAULT;
import static com.vaadin.flow.server.Constants.RESOURCES_JAR_DEFAULT;

/**
 * Copies JavaScript and CSS files from JAR files into a given folder.
 * <p>
 * For internal use only. May be renamed or removed in a future release.
 *
 * @since 2.0
 */
public class TaskCopyFrontendFiles implements FallibleCommand {
    private static final String[] WILDCARD_INCLUSIONS = new String[] {
            "**/*.js", "**/*.js.map", "**/*.css", "**/*.css.map", "**/*.ts",
            "**/*.ts.map", "**/*.tsx", "**/*.tsx.map" };
    private static final String WILDCARD_INCLUSION_APP_THEME_JAR = "**/themes/**/*";
    private final Options options;
    private Set<File> resourceLocations = null;

    /**
     * Scans the jar files given defined by {@code resourcesToScan}.
     *
     * @param targetDirectory
     *            target directory for the discovered files
     * @param resourcesToScan
     *            folders and jar files to scan.
     */
    TaskCopyFrontendFiles(Options options) {
        this.options = options;
        resourceLocations = options.jarFiles.stream().filter(File::exists)
                .collect(Collectors.toSet());
    }

    @Override
    public void execute() {
        long start = System.nanoTime();
        log().info("Copying frontend resources from jar files ...");
        File targetDirectory = options.getJarFrontendResourcesFolder();
        TaskCopyLocalFrontendFiles.createTargetFolder(targetDirectory);
        JarContentsManager jarContentsManager = new JarContentsManager();
        for (File location : resourceLocations) {
            if (location.isDirectory()) {
                TaskCopyLocalFrontendFiles.copyLocalResources(
                        new File(location, RESOURCES_FRONTEND_DEFAULT),
                        targetDirectory);
                TaskCopyLocalFrontendFiles.copyLocalResources(
                        new File(location,
                                COMPATIBILITY_RESOURCES_FRONTEND_DEFAULT),
                        targetDirectory);
            } else {
                jarContentsManager.copyIncludedFilesFromJarTrimmingBasePath(
                        location, RESOURCES_FRONTEND_DEFAULT, targetDirectory,
                        WILDCARD_INCLUSIONS);
                jarContentsManager.copyIncludedFilesFromJarTrimmingBasePath(
                        location, COMPATIBILITY_RESOURCES_FRONTEND_DEFAULT,
                        targetDirectory, WILDCARD_INCLUSIONS);
                jarContentsManager.copyIncludedFilesFromJarTrimmingBasePath(
                        location, RESOURCES_JAR_DEFAULT, targetDirectory,
                        WILDCARD_INCLUSION_APP_THEME_JAR);
            }
        }
        long ms = (System.nanoTime() - start) / 1000000;
        log().info("Visited {} resources. Took {} ms.",
                resourceLocations.size(), ms);
    }

    private Logger log() {
        return LoggerFactory.getLogger(this.getClass());
    }
}
