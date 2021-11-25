/*
 * Copyright 2000-2021 Vaadin Ltd.
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

package com.vaadin.base.devserver.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.testutil.TestUtils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import elemental.json.Json;
import elemental.json.JsonObject;
import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public class DevModeUsageStatisticsTest {

    public static final String DEFAULT_SERVER_MESSAGE = "{\"reportInterval\":86400,\"serverMessage\":\"\"}";
    public static final String SERVER_MESSAGE_MESSAGE = "{\"reportInterval\":86400,\"serverMessage\":\"Hello\"}";
    public static final String SERVER_MESSAGE_3H = "{\"reportInterval\":10800,\"serverMessage\":\"\"}";
    public static final String SERVER_MESSAGE_48H = "{\"reportInterval\":172800,\"serverMessage\":\"\"}";
    public static final String SERVER_MESSAGE_40D = "{\"reportInterval\":3456000,\"serverMessage\":\"\"}";
    private static final int HTTP_PORT = 8089;
    public static final String USAGE_REPORT_URL_LOCAL = "http://localhost:"
            + HTTP_PORT + "/";
    private static final long SEC_12H = 60 * 60 * 12;
    private static final long SEC_24H = 60 * 60 * 24;
    private static final long SEC_48H = 60 * 60 * 48;
    private static final long SEC_30D = 60 * 60 * 24 * 30;
    private static final String DEFAULT_PROJECT_ID = "12b7fc85f50e8c82cb6f4b03e12f2335";
    private StatisticsStorage storage;
    private StatisticsSender sender;
    private VaadinContext context;

    /**
     * Create a temporary file from given test resource.
     *
     * @param testResourceName
     *            Name of the test resource
     * @return Temporary file
     */
    private static File createTempStorage(String testResourceName)
            throws IOException {
        File original = new File(
                TestUtils.getTestResource(testResourceName).getFile());
        File result = File.createTempFile("test", "json");
        result.deleteOnExit();
        FileUtils.copyFile(original, result);
        return result;
    }

    @Before
    public void setup() throws Exception {
        storage = Mockito.spy(new StatisticsStorage());
        sender = Mockito.spy(new StatisticsSender(storage));

        // Change the file storage and reporting parameters for testing
        Mockito.when(sender.getReportingUrl())
                .thenReturn(USAGE_REPORT_URL_LOCAL);
        Mockito.when(storage.getUsageStatisticsFile()).thenReturn(
                createTempStorage("stats-data/usage-statistics-1.json"));
    }

    @Test
    public void clientData() throws Exception {
        // Init using test project
        String mavenProjectFolder = TestUtils
                .getTestFolder("stats-data/maven-project-folder1").toPath()
                .toString();
        DevModeUsageStatistics.init(mavenProjectFolder, storage, sender);

        String data = IOUtils.toString(
                TestUtils.getTestResource("stats-data/client-data-1.txt"),
                StandardCharsets.UTF_8);
        DevModeUsageStatistics.handleBrowserData(wrapStats(data));
    }

    @Test
    public void projectId() throws Exception {
        String mavenProjectFolder = TestUtils
                .getTestFolder("stats-data/maven-project-folder1").toPath()
                .toString();
        DevModeUsageStatistics.init(mavenProjectFolder, storage, sender);

        Assert.assertEquals(
                "pom" + ProjectHelpers.createHash("com.exampledemo"),
                storage.getProjectId());
    }

    @Test
    public void sourceId() throws Exception {
        String mavenProjectFolder = TestUtils
                .getTestFolder("stats-data/maven-project-folder1").toPath()
                .toString();
        DevModeUsageStatistics.init(mavenProjectFolder, storage, sender);

        ObjectNode json = storage.readProject();
        Assert.assertEquals("https://start.vaadin.com/test/1",
                json.get(StatisticsConstants.FIELD_SOURCE_ID).asText());
    }

    @Test
    public void aggregates() throws Exception {
        // Init using test project
        String mavenProjectFolder = TestUtils
                .getTestFolder("stats-data/maven-project-folder1").toPath()
                .toString();
        DevModeUsageStatistics.init(mavenProjectFolder, storage, sender);

        // Averate events
        DevModeUsageStatistics.collectEvent("aggregate", 1);

        StatisticsContainer projectData = new StatisticsContainer(
                storage.readProject());

        Assert.assertEquals("Min does not match", 1,
                projectData.getValueAsDouble("aggregate_min"), 0);
        Assert.assertEquals("Max does not match", 1,
                projectData.getValueAsDouble("aggregate_max"), 0);
        Assert.assertEquals("Average does not match", 1,
                projectData.getValueAsDouble("aggregate_avg"), 0);
        Assert.assertEquals("Count does not match", 1,
                projectData.getValueAsInt("aggregate_count"));

        DevModeUsageStatistics.collectEvent("aggregate", 2);
        projectData = new StatisticsContainer(storage.readProject());
        Assert.assertEquals("Min does not match", 1,
                projectData.getValueAsDouble("aggregate_min"), 0);
        Assert.assertEquals("Max does not match", 2,
                projectData.getValueAsDouble("aggregate_max"), 0);
        Assert.assertEquals("Average does not match", 1.5,
                projectData.getValueAsDouble("aggregate_avg"), 0);
        Assert.assertEquals("Count does not match", 2,
                projectData.getValueAsInt("aggregate_count"));

        DevModeUsageStatistics.collectEvent("aggregate", 3);
        projectData = new StatisticsContainer(storage.readProject());

        Assert.assertEquals("Min does not match", 1,
                projectData.getValueAsDouble("aggregate_min"), 0);
        Assert.assertEquals("Max does not match", 3,
                projectData.getValueAsDouble("aggregate_max"), 0);
        Assert.assertEquals("Average does not match", 2,
                projectData.getValueAsDouble("aggregate_avg"), 0);
        Assert.assertEquals("Count does not match", 3,
                projectData.getValueAsInt("aggregate_count"));

        // Test count events
        DevModeUsageStatistics.collectEvent("count");
        projectData = new StatisticsContainer(storage.readProject());

        Assert.assertEquals("Increment does not match", 1,
                projectData.getValueAsInt("count"));
        DevModeUsageStatistics.collectEvent("count");
        projectData = new StatisticsContainer(storage.readProject());
        Assert.assertEquals("Increment does not match", 2,
                projectData.getValueAsInt("count"));
        DevModeUsageStatistics.collectEvent("count");
        projectData = new StatisticsContainer(storage.readProject());
        Assert.assertEquals("Increment does not match", 3,
                projectData.getValueAsInt("count"));

    }

    @Test
    public void multipleProjects() throws Exception {
        // Init using test project
        String mavenProjectFolder = TestUtils
                .getTestFolder("stats-data/maven-project-folder1").toPath()
                .toString();
        DevModeUsageStatistics.init(mavenProjectFolder, storage, sender);
        StatisticsContainer projectData = new StatisticsContainer(
                storage.readProject());
        // Data contains 5 previous starts for this project
        Assert.assertEquals("Expected to have 6 restarts", 6,
                projectData.getValueAsInt("devModeStarts"));

        // Switch project to track
        String mavenProjectFolder2 = TestUtils
                .getTestFolder("stats-data/maven-project-folder2").toPath()
                .toString();
        DevModeUsageStatistics.init(mavenProjectFolder2, storage, sender);
        projectData = new StatisticsContainer(storage.readProject());
        Assert.assertEquals("Expected to have one restarts", 1,
                projectData.getValueAsInt("devModeStarts"));

        // Switch project to track
        String gradleProjectFolder1 = TestUtils
                .getTestFolder("stats-data/gradle-project-folder1").toPath()
                .toString();
        DevModeUsageStatistics.init(gradleProjectFolder1, storage, sender);
        projectData = new StatisticsContainer(storage.readProject());
        Assert.assertEquals("Expected to have one restarts", 1,
                projectData.getValueAsInt("devModeStarts"));

        // Switch project to track
        String gradleProjectFolder2 = TestUtils
                .getTestFolder("stats-data/gradle-project-folder2").toPath()
                .toString();

        // Double init to check restart count
        DevModeUsageStatistics.init(gradleProjectFolder2, storage, sender);
        DevModeUsageStatistics.init(gradleProjectFolder2, storage, sender);
        projectData = new StatisticsContainer(storage.readProject());
        Assert.assertEquals("Expected to have 2 restarts", 2,
                projectData.getValueAsInt("devModeStarts"));

        // Check that all project are stored correctly
        ObjectNode allData = storage.read();
        Assert.assertEquals("Expected to have 4 projects", 4,
                getNumberOfProjects(allData));

    }

    @Test
    public void send() throws Exception {

        // Init using test project
        String mavenProjectFolder = TestUtils
                .getTestFolder("stats-data/maven-project-folder1").toPath()
                .toString();
        DevModeUsageStatistics.init(mavenProjectFolder, storage, sender);

        // Test with default server response
        try (TestHttpServer server = new TestHttpServer(200,
                DEFAULT_SERVER_MESSAGE)) {
            ObjectNode fullStats = storage.read();
            long lastSend = sender.getLastSendTime(fullStats);
            sender.sendStatistics(fullStats);

            fullStats = storage.read();
            long newSend = sender.getLastSendTime(fullStats);
            Assert.assertTrue("Send time should be updated",
                    newSend > lastSend);
            Assert.assertTrue("Status should be 200",
                    sender.getLastSendStatus(fullStats).contains("200"));
            Assert.assertEquals("Default interval should be 24H in seconds",
                    SEC_24H, sender.getInterval(fullStats));
        }

        // Test with server response with too custom interval
        try (TestHttpServer server = new TestHttpServer(200,
                SERVER_MESSAGE_48H)) {
            ObjectNode fullStats = storage.read();
            long lastSend = sender.getLastSendTime(fullStats);
            sender.sendStatistics(fullStats);
            fullStats = storage.read();
            long newSend = sender.getLastSendTime(fullStats);
            Assert.assertTrue("Send time should be updated",
                    newSend > lastSend);
            Assert.assertTrue("Status should be 200",
                    sender.getLastSendStatus(fullStats).contains("200"));
            Assert.assertEquals("Custom interval should be 48H in seconds",
                    SEC_48H, sender.getInterval(fullStats));
        }

        // Test with server response with too short interval
        try (TestHttpServer server = new TestHttpServer(200,
                SERVER_MESSAGE_3H)) {
            ObjectNode fullStats = storage.read();
            long lastSend = sender.getLastSendTime(fullStats);
            sender.sendStatistics(fullStats);
            fullStats = storage.read();
            long newSend = sender.getLastSendTime(fullStats);
            Assert.assertTrue("Send time should be updated",
                    newSend > lastSend);
            Assert.assertTrue("Status should be 200",
                    sender.getLastSendStatus(fullStats).contains("200"));
            Assert.assertEquals("Minimum interval should be 12H in seconds",
                    SEC_12H, sender.getInterval(fullStats));
        }

        // Test with server response with too long interval
        try (TestHttpServer server = new TestHttpServer(200,
                SERVER_MESSAGE_40D)) {
            ObjectNode fullStats = storage.read();
            long lastSend = sender.getLastSendTime(fullStats);
            sender.sendStatistics(fullStats);
            fullStats = storage.read();
            long newSend = sender.getLastSendTime(fullStats);
            Assert.assertTrue("Send time should be not be updated",
                    newSend > lastSend);
            Assert.assertTrue("Status should be 200",
                    sender.getLastSendStatus(fullStats).contains("200"));
            Assert.assertEquals("Maximum interval should be 30D in seconds",
                    SEC_30D, sender.getInterval(fullStats));
        }

        // Test with server fail response
        try (TestHttpServer server = new TestHttpServer(500,
                SERVER_MESSAGE_40D)) {
            ObjectNode fullStats = storage.read();
            long lastSend = sender.getLastSendTime(fullStats);
            sender.sendStatistics(fullStats);
            fullStats = storage.read();

            long newSend = sender.getLastSendTime(fullStats);
            Assert.assertTrue("Send time should be updated",
                    newSend > lastSend);
            Assert.assertTrue("Status should be 500",
                    sender.getLastSendStatus(fullStats).contains("500"));
            Assert.assertEquals(
                    "In case of errors we should use default interval", SEC_24H,
                    sender.getInterval(fullStats));
        }

        // Test with server returned message
        try (TestHttpServer server = new TestHttpServer(200,
                SERVER_MESSAGE_MESSAGE)) {
            ObjectNode fullStats = storage.read();

            long lastSend = sender.getLastSendTime(fullStats);
            sender.sendStatistics(fullStats);
            fullStats = storage.read();
            long newSend = sender.getLastSendTime(fullStats);
            Assert.assertTrue("Send time should be updated",
                    newSend > lastSend);
            Assert.assertTrue("Status should be 200",
                    sender.getLastSendStatus(fullStats).contains("200"));
            Assert.assertEquals("Default interval should be 24H in seconds",
                    SEC_24H, sender.getInterval(fullStats));
            Assert.assertEquals("Message should be returned", "Hello",
                    sender.getLastServerMessage(fullStats));
        }

    }

    @Test
    public void mavenProjectProjectId() {
        String mavenProjectFolder1 = TestUtils
                .getTestFolder("stats-data/maven-project-folder1").toPath()
                .toString();
        String mavenProjectFolder2 = TestUtils
                .getTestFolder("stats-data/maven-project-folder2").toPath()
                .toString();
        String id1 = ProjectHelpers.generateProjectId(mavenProjectFolder1);
        String id2 = ProjectHelpers.generateProjectId(mavenProjectFolder2);
        Assert.assertNotNull(id1);
        Assert.assertNotNull(id2);
        Assert.assertNotEquals(id1, id2); // Should differ
    }

    @Test
    public void mavenProjectSource() {
        String mavenProjectFolder1 = TestUtils
                .getTestFolder("stats-data/maven-project-folder1").toPath()
                .toString();
        String mavenProjectFolder2 = TestUtils
                .getTestFolder("stats-data/maven-project-folder2").toPath()
                .toString();
        String source1 = ProjectHelpers.getProjectSource(mavenProjectFolder1);
        String source2 = ProjectHelpers.getProjectSource(mavenProjectFolder2);
        Assert.assertEquals("https://start.vaadin.com/test/1", source1);
        Assert.assertEquals("https://start.vaadin.com/test/2", source2);
    }

    @Test
    public void gradleProjectProjectId() {
        String gradleProjectFolder1 = TestUtils
                .getTestFolder("stats-data/gradle-project-folder1").toPath()
                .toString();
        String gradleProjectFolder2 = TestUtils
                .getTestFolder("stats-data/gradle-project-folder2").toPath()
                .toString();
        String id1 = ProjectHelpers.generateProjectId(gradleProjectFolder1);
        String id2 = ProjectHelpers.generateProjectId(gradleProjectFolder2);
        Assert.assertNotNull(id1);
        Assert.assertNotNull(id2);
        Assert.assertNotEquals(id1, id2); // Should differ
    }

    @Test
    public void gradleProjectSource() {
        String gradleProjectFolder1 = TestUtils
                .getTestFolder("stats-data/gradle-project-folder1").toPath()
                .toString();
        String gradleProjectFolder2 = TestUtils
                .getTestFolder("stats-data/gradle-project-folder2").toPath()
                .toString();
        String source1 = ProjectHelpers.getProjectSource(gradleProjectFolder1);
        String source2 = ProjectHelpers.getProjectSource(gradleProjectFolder2);
        Assert.assertEquals("https://start.vaadin.com/test/3", source1);
        Assert.assertEquals("https://start.vaadin.com/test/4", source2);
    }

    @Test
    public void missingProject() {
        String mavenProjectFolder1 = TestUtils.getTestFolder("java").toPath()
                .toString();
        String mavenProjectFolder2 = TestUtils.getTestFolder("stats-data/empty")
                .toPath().toString();
        String id1 = ProjectHelpers.generateProjectId(mavenProjectFolder1);
        String id2 = ProjectHelpers.generateProjectId(mavenProjectFolder2);
        Assert.assertEquals(DEFAULT_PROJECT_ID, id1);
        Assert.assertEquals(DEFAULT_PROJECT_ID, id2); // Should be the
                                                      // default
                                                      // id in both
                                                      // cases
    }

    @Test
    public void readUserKey() throws IOException {
        String mavenProjectFolder = TestUtils
                .getTestFolder("stats-data/maven-project-folder1").toPath()
                .toString();
        System.setProperty("user.home",
                TestUtils.getTestFolder("stats-data").toPath().toString()); // Change
                                                                            // the
                                                                            // home
                                                                            // location
        DevModeUsageStatistics.init(mavenProjectFolder, storage, sender);

        // Read from file
        String keyString = "user-ab641d2c-test-test-file-223cf1fa628e";
        String key = ProjectHelpers.getUserKey();
        assertEquals(keyString, key);

        // Try with non existent
        File tempDir = File.createTempFile("user.home", "test");
        tempDir.delete(); // Delete
        tempDir.mkdir(); // Recreate as directory
        tempDir.deleteOnExit();
        File vaadinHome = new File(tempDir, ".vaadin");
        vaadinHome.mkdir();
        System.setProperty("user.home", tempDir.getAbsolutePath()); // Change
                                                                    // the
                                                                    // home
                                                                    // location
        String newKey = ProjectHelpers.getUserKey();
        assertNotNull(newKey);
        assertNotSame(keyString, newKey);
        File userKeyFile = new File(vaadinHome, "userKey");
        Assert.assertTrue("userKey should be created automatically",
                userKeyFile.exists());
    }

    @Test
    public void readProKey() {
        String mavenProjectFolder = TestUtils
                .getTestFolder("stats-data/maven-project-folder1").toPath()
                .toString();
        System.setProperty("user.home",
                TestUtils.getTestFolder("stats-data").toPath().toString()); // Change
                                                                            // the
                                                                            // home
                                                                            // location
        DevModeUsageStatistics.init(mavenProjectFolder, storage, sender);

        // File is used by default
        String keyStringFile = "test@vaadin.com/pro-536e1234-test-test-file-f7a1ef311234";
        String keyFile = ProjectHelpers.getProKey();
        assertEquals(keyStringFile, "test@vaadin.com/" + keyFile);

        // Check system property works
        String keyStringProp = "test@vaadin.com/pro-536e1234-test-test-prop-f7a1ef311234";
        System.setProperty("vaadin.proKey", keyStringProp);
        String keyProp = ProjectHelpers.getProKey();
        assertEquals(keyStringProp, "test@vaadin.com/" + keyProp);
    }

    /**
     * Simple HttpServer for testing.
     */
    public static class TestHttpServer implements AutoCloseable {

        private HttpServer httpServer;
        private String lastRequestContent;

        public TestHttpServer(int code, String response) throws Exception {
            this.httpServer = createStubGatherServlet(HTTP_PORT, code,
                    response);
        }

        private HttpServer createStubGatherServlet(int port, int status,
                String response) throws Exception {
            HttpServer httpServer = HttpServer
                    .create(new InetSocketAddress(port), 0);
            httpServer.createContext("/", exchange -> {
                this.lastRequestContent = IOUtils.toString(
                        exchange.getRequestBody(), Charset.defaultCharset());
                exchange.sendResponseHeaders(status, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            });
            httpServer.start();
            return httpServer;
        }

        public String getLastRequestContent() {
            return lastRequestContent;
        }

        @Override
        public void close() throws Exception {
            if (httpServer != null) {
                httpServer.stop(0);
            }
        }

    }

    private static JsonObject wrapStats(String data) {
        JsonObject wrapped = Json.createObject();
        wrapped.put("browserData", data);
        return wrapped;
    }

    private static int getNumberOfProjects(ObjectNode allData) {
        if (allData.has(StatisticsConstants.FIELD_PROJECTS)) {
            return allData.get(StatisticsConstants.FIELD_PROJECTS).size();
        }
        return 0;
    }

}
