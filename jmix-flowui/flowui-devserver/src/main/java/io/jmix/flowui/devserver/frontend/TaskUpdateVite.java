/*
 * Copyright 2000-2023 Vaadin Ltd.
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

package io.jmix.flowui.devserver.frontend;

import com.vaadin.flow.server.frontend.FallibleCommand;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Updates the Vite configuration files according with current project settings.
 */
public class TaskUpdateVite implements FallibleCommand, Serializable {

    private final Options options;

    private final Set<String> webComponentTags;

    TaskUpdateVite(Options options, Set<String> webComponentTags) {
        this.options = options;
        this.webComponentTags = webComponentTags;
    }

    @Override
    public void execute() {
        try {
            createConfig();
            createGeneratedConfig();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void createConfig() throws IOException {
        File configFile = new File(options.getStudioFolder(),
                FrontendUtils.VITE_CONFIG);

        try (InputStream resource = FrontendUtils.getResourceAsStream(FrontendUtils.VITE_CONFIG)) {
            String template = IOUtils.toString(resource, StandardCharsets.UTF_8);
            int freePort;
            try {
                freePort = FrontendUtils.findFreePort(60_000, 65_000);
            } catch (Exception e) {
                freePort = new Random().nextInt(60_000, 65_000);
            }

            template = template.replace("60001", String.valueOf(freePort));

            if (!configFile.exists()) {
                configFile.createNewFile();
                FileUtils.write(configFile, template, StandardCharsets.UTF_8);
                String message = String.format("Created vite configuration file: '%s'", configFile);
                log().debug(message);
                FrontendUtils.logInFile(message);
            } else {
                List<String> viteConfigLines = FileUtils.readLines(configFile, StandardCharsets.UTF_8);
                final String hmrPortConstDeclaration = "let hmrPort = " + freePort + ";";
                for (String line : viteConfigLines) {
                    if (line.trim().contains("let hmrPort")) {
                        viteConfigLines.set(
                                viteConfigLines.indexOf(line),
                                hmrPortConstDeclaration
                        );
                    }
                }
                FileUtils.writeLines(configFile, viteConfigLines);
                FrontendUtils.logInFile(String.format("Vite configuration '%s' has been updated", configFile));
            }
        }
    }

    private void createGeneratedConfig() throws IOException {
        // Always overwrite this
        File generatedConfigFile = new File(options.getStudioFolder(), FrontendUtils.VITE_GENERATED_CONFIG);
        try (InputStream resource = FrontendUtils.getResourceAsStream(
                FrontendUtils.VITE_GENERATED_CONFIG)) {
            String buildFolder = options.getBuildDirectoryName();
            String template = IOUtils.toString(resource, StandardCharsets.UTF_8);
            String relativePath = "./" + buildFolder.substring(buildFolder.indexOf("/build") + 1);
            template = template
                    .replace("#settingsImport#", relativePath + "/" + TaskUpdateSettingsFile.DEV_SETTINGS_FILE)
                    .replace("#buildFolder#", relativePath)
                    .replace("#webComponentTags#",
                            webComponentTags == null || webComponentTags.isEmpty()
                                    ? ""
                                    : String.join(";", webComponentTags));
            FileUtils.write(generatedConfigFile, template, StandardCharsets.UTF_8);
            log().debug("Created vite generated configuration file: '{}'", generatedConfigFile);
        }
    }

    private Logger log() {
        return LoggerFactory.getLogger(this.getClass());
    }
}
