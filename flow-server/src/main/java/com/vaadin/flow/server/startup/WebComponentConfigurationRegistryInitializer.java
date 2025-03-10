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
package com.vaadin.flow.server.startup;

import jakarta.servlet.annotation.HandlesTypes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.WebComponentExporter;
import com.vaadin.flow.component.WebComponentExporterFactory;
import com.vaadin.flow.component.webcomponent.WebComponentConfiguration;
import com.vaadin.flow.internal.CustomElementNameValidator;
import com.vaadin.flow.server.InvalidCustomElementNameException;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.webcomponent.WebComponentConfigurationRegistry;
import com.vaadin.flow.server.webcomponent.WebComponentExporterUtils;

/**
 * Servlet initializer for collecting all classes that extend
 * {@link WebComponentExporter}/{@link WebComponentExporterFactory} on startup,
 * creates unique {@link WebComponentConfiguration} instances, and adds them to
 * {@link WebComponentConfigurationRegistry}.
 * <p>
 * For internal use only. May be renamed or removed in a future release.
 *
 * @author Vaadin Ltd.
 * @since 2.0
 */
@HandlesTypes({ WebComponentExporter.class, WebComponentExporterFactory.class })
public class WebComponentConfigurationRegistryInitializer
        implements VaadinServletContextStartupInitializer {

    @Override
    @SuppressWarnings("rawtypes")
    public void initialize(Set<Class<?>> classSet, VaadinContext context)
            throws VaadinInitializerException {
        WebComponentConfigurationRegistry instance = WebComponentConfigurationRegistry
                .getInstance(context);

        classSet = AbstractAnnotationValidator
                .removeHandleTypesSelfReferences(classSet, this);

        if (classSet.isEmpty()) {
            instance.setConfigurations(Collections.emptySet());
            return;
        }

        try {
            Set<WebComponentExporterFactory> factories = WebComponentExporterUtils
                    .getFactories(classSet);
            Set<WebComponentConfiguration<? extends Component>> configurations = constructConfigurations(
                    factories);

            validateTagNames(configurations);
            validateDistinctTagNames(configurations);

            instance.setConfigurations(configurations);
        } catch (Exception e) {
            throw new VaadinInitializerException(
                    String.format("%s failed to collect %s implementations!",
                            WebComponentConfigurationRegistryInitializer.class
                                    .getSimpleName(),
                            WebComponentExporter.class.getSimpleName()),
                    e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Set<WebComponentConfiguration<? extends Component>> constructConfigurations(
            Set<WebComponentExporterFactory> factories) {
        Objects.requireNonNull(factories,
                "Parameter 'exporterClasses' " + "cannot be null!");

        final WebComponentExporter.WebComponentConfigurationFactory factory = new WebComponentExporter.WebComponentConfigurationFactory();

        Stream<WebComponentConfiguration<? extends Component>> stream = factories
                .stream().map(WebComponentExporterFactory::create)
                .map(factory::create);
        return stream.collect(Collectors.toSet());
    }

    /**
     * Validate that all web component names are valid custom element names.
     *
     * @param configurationSet
     *            set of web components to validate
     */
    private static void validateTagNames(
            Set<WebComponentConfiguration<? extends Component>> configurationSet) {
        for (WebComponentConfiguration<? extends Component> configuration : configurationSet) {
            if (!CustomElementNameValidator
                    .isCustomElementName(configuration.getTag())) {
                throw new InvalidCustomElementNameException(String.format(
                        "Tag name '%s' given by '%s' is not a valid custom "
                                + "element name.",
                        configuration.getTag(),
                        configuration.getExporterClass().getCanonicalName()));
            }
        }
    }

    /**
     * Validate that we have exactly one {@link WebComponentConfiguration} per
     * tag name.
     *
     * @param configurationSet
     *            set of web components to validate
     */
    private static void validateDistinctTagNames(
            Set<WebComponentConfiguration<? extends Component>> configurationSet) {
        long count = configurationSet.stream()
                .map(WebComponentConfiguration::getTag).distinct().count();
        if (configurationSet.size() != count) {
            Map<String, WebComponentConfiguration<? extends Component>> items = new HashMap<>();
            for (WebComponentConfiguration<? extends Component> configuration : configurationSet) {
                String tag = configuration.getTag();
                if (items.containsKey(tag)) {
                    String message = String.format(
                            "Found two %s classes '%s' and '%s' for the tag "
                                    + "name '%s'. Tag must be unique.",
                            WebComponentExporter.class.getSimpleName(),
                            items.get(tag).getExporterClass()
                                    .getCanonicalName(),
                            configuration.getExporterClass().getCanonicalName(),
                            tag);
                    throw new IllegalArgumentException(message);
                }
                items.put(tag, configuration);
            }
        }
    }
}
