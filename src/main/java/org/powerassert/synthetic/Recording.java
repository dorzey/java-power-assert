/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.powerassert.synthetic;

import java.util.List;

public class Recording<T> {
	private T value;
	private List<RecordedExpression<T>> recordedExprs;

	public Recording(T value, List<RecordedExpression<T>> recordedExprs) {
		this.value = value;
		this.recordedExprs = recordedExprs;
	}

	public T getValue() {
		return value;
	}

	public List<RecordedExpression<T>> getRecordedExprs() {
		return recordedExprs;
	}
}
