/* Copyright 2016 hbz NRW (http://www.hbz-nrw.de/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package de.nrw.hbz.regal.sync.ingest;

/**
 * This class is a collection of properties of a website version. These
 * properties will be relevant when importing the website version into the
 * Regal-API. The properties are extracted by the WebsiteMetadataExtractor from
 * the heritage data.
 * 
 * The property names (field names) correspond to the field names of the Fedora
 * and Regal Objects as far as possible.
 * 
 * @author I. Kuss, kuss@hbz-nrw.de
 */
class WebsiteVersionMetadata {

	private HasData hasData = null;
	private IsDescribedBy isDescribedBy = null;
	private Edo2Data edo2Data = null;
	private String accessScheme = null;
	private String catalogId = null;
	private String contentType = null;
	private String localDir = null;

	/**
	 * Der Konstruktor
	 */
	public WebsiteVersionMetadata() {
		this.hasData = new HasData();
		this.isDescribedBy = new IsDescribedBy();
		this.edo2Data = new Edo2Data();
	}

	/*
	 * Getter- und Setter-Methoden
	 */
	public HasData getHasData() {
		return this.hasData;
	}

	public IsDescribedBy getIsDescribedBy() {
		return this.isDescribedBy;
	}

	public Edo2Data getEdo2Data() {
		return this.edo2Data;
	}

	public void setAccessScheme(String accessScheme) {
		this.accessScheme = accessScheme;
	}

	public String getAccessScheme() {
		return this.accessScheme;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public String getContentType() {
		return this.contentType;
	}

	public void setCatalogId(String catalogId) {
		this.catalogId = catalogId;
	}

	public String getCatalogId() {
		return this.catalogId;
	}

	public void setLocalDir(String localDir) {
		this.localDir = localDir;
	}

	public String getLocalDir() {
		return this.localDir;
	}

	/*
	 * Inline-Klassen Diese Entsprechen Blöcken in der json-Representation der
	 * Daten
	 */

	/*
	 * skalare Daten, die im json-Block "hasData" zusammengefasst sind.
	 */
	class HasData {
		private String format = null;
		private String fileLabel = null;
		private String size = null;

		private HasData() {
			// leerer Konstruktor
		}

		public void setFormat(String format) {
			this.format = format;
		}

		public String getFormat() {
			return this.format;
		}

		public void setFileLabel(String fileLabel) {
			this.fileLabel = fileLabel;
		}

		public String getFileLabel() {
			return this.fileLabel;
		}

		public void setSize(String size) {
			this.size = size;
		}

		public String getSize() {
			return this.size;
		}

	} // end of inline class HasData

	/*
	 * skalare Daten, die im json-Block "isDescribedBy" zusammengefasst sind.
	 */
	class IsDescribedBy {
		private String inputFrom = null; /* die URL */
		private String created = null;
		private String modified = null;

		private IsDescribedBy() {
			// leerer Konstruktor
		}

		public void setInputFrom(String inputFrom) {
			this.inputFrom = inputFrom;
		}

		public String getInputFrom() {
			return this.inputFrom;
		}

		public void setCreated(String created) {
			this.created = created;
		}

		public String getCreated() {
			return this.created;
		}

		public void setModified(String modified) {
			this.modified = modified;
		}

		public String getModified() {
			return this.modified;
		}

	} // end of inline class IsDescribedBy

	/*
	 * eine Sammlung skalarer Daten, die direkt aus Edoweb2 übernommen werden.
	 * z.T. nur z. Info (also unwichtig), z.T. aber auch wichtige Daten, aus
	 * denen weiterführende Daten für Edoweb3 extrahiert werden, z.B. ingestId
	 * => urlId => (über Konkordanz) Edoweb3-ID
	 */
	class Edo2Data {
		private String pid = null;
		private String label = null;
		private String ingestId = null;
		private String entityType = null;
		private String usageType = null;
		private String urlId = null;

		private Edo2Data() {
			// der Konstruktor - bruuche mer, ävver is nix drin
			// Die Initialisierung der Instanzvariablen findet direkt bei deren
			// Deklaration statt (s.o.)
		}

		public void setPid(String pid) {
			this.pid = pid;
		}

		public String getPid() {
			return this.pid;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public String getLabel() {
			return this.label;
		}

		public void setIngestId(String ingestId) {
			this.ingestId = ingestId;
		}

		public String getIngestId() {
			return this.ingestId;
		}

		public void setEntityType(String entityType) {
			this.entityType = entityType;
		}

		public String getEntityType() {
			return this.entityType;
		}

		public void setUsageType(String usageType) {
			this.usageType = usageType;
		}

		public String getUsageType() {
			return this.usageType;
		}

		public void setUrlId(String urlId) {
			this.urlId = urlId;
		}

		public String getUrlId() {
			return this.urlId;
		}

	} // end of inline-Class Edo2Data

}
