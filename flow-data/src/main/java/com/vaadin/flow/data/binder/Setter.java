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
package com.vaadin.flow.data.binder;

import java.io.Serializable;
import java.util.function.BiConsumer;

import com.vaadin.flow.component.HasValue;

/**
 * The function to write the field value to the bean property
 *
 * @see BiConsumer
 * @see Binder#bind(HasValue, ValueProvider, Setter)
 * @param <BEAN>
 *            the type of the target bean
 * @param <FIELDVALUE>
 *            the field value type to be written to the bean
 *
 * @author Vaadin Ltd
 * @since 1.0
 *
 */
@FunctionalInterface
public interface Setter<BEAN, FIELDVALUE>
        extends BiConsumer<BEAN, FIELDVALUE>, Serializable {

    /**
     * Save value to the bean property.
     *
     * @param bean
     *            the target bean
     * @param fieldvalue
     *            the field value to be written to the bean
     */
    @Override
    void accept(BEAN bean, FIELDVALUE fieldvalue);
}
