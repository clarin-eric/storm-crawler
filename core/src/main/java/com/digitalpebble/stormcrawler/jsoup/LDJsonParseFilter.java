/**
 * Licensed to DigitalPebble Ltd under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * DigitalPebble licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpebble.stormcrawler.jsoup;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.JSoupFilter;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Extracts data from JSON-LD representation (https://json-ld.org/).
 * Illustrates how to use the JSoupFilters
 **/
public class LDJsonParseFilter extends JSoupFilter {

	public static final Logger LOG = LoggerFactory.getLogger(LDJsonParseFilter.class);

	private static ObjectMapper mapper = new ObjectMapper();

	private List<LabelledJsonPointer> expressions = new LinkedList<>();

	public static JsonNode filterJson(Document doc) throws Exception {

		Element el = doc.selectFirst("script[type=application/ld+json]");
		if (el == null) {
			return null;
		}
		return mapper.readValue(el.data(), JsonNode.class);
	}

	public void configure(@SuppressWarnings("rawtypes") Map stormConf, JsonNode filterParams) {
		java.util.Iterator<Entry<String, JsonNode>> iter = filterParams.fields();
		while (iter.hasNext()) {
			Entry<String, JsonNode> entry = iter.next();
			String key = entry.getKey();
			JsonNode node = entry.getValue();
			LabelledJsonPointer labelP = new LabelledJsonPointer(key, JsonPointer.valueOf(node.asText()));
			expressions.add(labelP);
		}
	}

	class LabelledJsonPointer {

		String label;
		JsonPointer pointer;

		public LabelledJsonPointer(String label, JsonPointer pointer) {
			this.label = label;
			this.pointer = pointer;
		}

		@Override
		public String toString() {
			return label + " => " + pointer.toString();
		}
	}

	@Override
	public void filter(String URL, byte[] content, Document doc, ParseResult parse) {
		if (doc == null) {
			return;
		}
		try {
			JsonNode json = filterJson(doc);
			if (json == null) {
				return;
			}

			ParseData parseData = parse.get(URL);
			Metadata metadata = parseData.getMetadata();

			// extract patterns and store as metadata
			for (LabelledJsonPointer expression : expressions) {
				JsonNode match = json.at(expression.pointer);
				if (match.isMissingNode()) {
					continue;
				}
				metadata.addValue(expression.label, match.asText());
			}

		} catch (Exception e) {
			LOG.error("Exception caught when extracting json", e);
		}

	}

}
