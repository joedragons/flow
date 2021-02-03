/*
 * Copyright 2000-2020 Vaadin Ltd.
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

package com.vaadin.flow.component;

import java.util.concurrent.atomic.AtomicInteger;

import com.vaadin.flow.component.ComponentTest.TracksAttachDetach;

public class CompositeTextNodeTest extends CompositeTest {

    static class TracksAttachDetachText extends Text
            implements TracksAttachDetach {

        public TracksAttachDetachText(String text) {
            super(text);
        }

        private AtomicInteger attachEvents = new AtomicInteger();
        private AtomicInteger detachEvents = new AtomicInteger();

        @Override
        public AtomicInteger getAttachEvents() {
            return attachEvents;
        }

        @Override
        public AtomicInteger getDetachEvents() {
            return detachEvents;
        }

    }

    @Override
    protected Component createTestComponent() {
        return new TracksAttachDetachText("Test component");
    }
}
