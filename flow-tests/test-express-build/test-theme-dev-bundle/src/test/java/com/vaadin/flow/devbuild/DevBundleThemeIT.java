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

package com.vaadin.flow.devbuild;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import net.jcip.annotations.NotThreadSafe;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

import com.vaadin.flow.testutil.ChromeBrowserTest;

@NotThreadSafe
public class DevBundleThemeIT extends ChromeBrowserTest {

    private static final String RED_COLOR = "rgba(255, 0, 0, 1)";
    private static final String GREEN_COLOR = "rgba(0, 255, 0, 1)";
    private static final String BLUE_COLOR = "rgba(0, 0, 255, 1)";
    private static final String THEME_FOLDER = "frontend/themes/my-theme/";

    private File fontFile;

    private File stylesCss;
    private File themeAssetsInBundle;

    @Before
    public void init() {
        File baseDir = new File(System.getProperty("user.dir", "."));
        themeAssetsInBundle = new File(baseDir,
                "dev-bundle/assets/themes/my-theme");
        final File themeFolder = new File(baseDir, THEME_FOLDER);
        fontFile = new File(themeFolder, "fonts/ostrich-sans-regular.ttf");
        stylesCss = new File(themeFolder, "styles.css");
    }

    @After
    public void cleanUp() {
        doActionAndWaitUntilLiveReloadComplete(
                () -> changeBackgroundColor(GREEN_COLOR, RED_COLOR));
    }

    @Test
    public void serveStylesInExpressBuildMode_changeStyles_stylesUpdatedWithoutBundleRecompilation() {
        open();

        waitUntilInitialStyles();
        checkLogsForErrors();

        // Live reload upon styles.css change
        doActionAndWaitUntilLiveReloadComplete(
                () -> changeBackgroundColor(RED_COLOR, GREEN_COLOR));
        waitUntilCustomBackgroundColor();
        checkLogsForErrors();
    }

    @Test
    public void serveStylesInExpressBuildMode_assetsAreCopiedToBundle() {
        open();

        verifyFontInBundle();
        verifyExternalAssetInBundle();

        waitUntilCustomFont();
        waitUntilExternalAsset();
        waitUntilImportedFrontendStyles();

        checkLogsForErrors();
    }

    private void waitUntilImportedFrontendStyles() {
        waitUntil(driver -> {
            WebElement paragraph = findElement(By.tagName("p"));
            if (paragraph != null) {
                String color = paragraph.getCssValue("color");
                Assert.assertEquals(BLUE_COLOR, color);
                return true;
            }
            return false;
        });
    }

    private void waitUntilExternalAsset() {
        waitUntil(driver -> {
            WebElement icon = findElement(By.tagName("i"));
            if (icon != null) {
                String classAttr = icon.getAttribute("class");
                Assert.assertEquals("las la-cat", classAttr);
                return true;
            }
            return false;
        });
    }

    private void verifyExternalAssetInBundle() {
        File lineAwesome = new File(themeAssetsInBundle,
                "line-awesome/dist/line-awesome/css/line-awesome.min.css");
        Assert.assertTrue("External asset file is not found in the bundle",
                lineAwesome.exists());
    }

    private void verifyFontInBundle() {
        File font = new File(themeAssetsInBundle,
                "fonts/" + fontFile.getName());
        Assert.assertTrue("Font file is not found in the bundle",
                font.exists());
    }

    private void waitUntilCustomBackgroundColor() {
        waitUntil(driver -> isCustomBackGroundColor());
    }

    private void waitUntilInitialStyles() {
        waitUntil(driver -> !isCustomBackGroundColor());
    }

    private void waitUntilCustomFont() {
        waitUntil(driver -> isCustomFont());
    }

    private boolean isCustomBackGroundColor() {
        try {
            final WebElement body = findElement(By.tagName("body"));
            return GREEN_COLOR.equals(body.getCssValue("background-color"));
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

    private boolean isCustomFont() {
        try {
            final WebElement body = findElement(By.tagName("body"));
            return "Ostrich".equals(body.getCssValue("font-family"));
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

    private void changeBackgroundColor(String from, String to) {
        try {
            String content = FileUtils.readFileToString(stylesCss,
                    StandardCharsets.UTF_8);
            content = content.replace(from, to);
            FileUtils.write(stylesCss, content, StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Failed to apply new background styles",
                    e);
        }
    }

    private void doActionAndWaitUntilLiveReloadComplete(Runnable action) {
        // Add a new active client with 'blocker' key and let the
        // waitForVaadin() to block until new page/document will be loaded as a
        // result of live reload.
        executeScript(
                "window.Vaadin.Flow.clients[\"blocker\"] = {isActive: () => true};");
        action.run();
        getCommandExecutor().waitForVaadin();
    }

}
