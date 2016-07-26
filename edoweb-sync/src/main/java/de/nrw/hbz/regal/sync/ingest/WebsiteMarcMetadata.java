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
 * This class is a collection of bibliographic MARC Metadata used for Websites
 * (Edoweb contentType "website" and "version" (of website)). The data are
 * collected by MetadataExtractor.main(). Only data that are relevant for
 * importing websites are collected. These data will be useful for the import of
 * heritage data (websites) into the Regal-API.
 * 
 * @author I. Kuss, hbz, kuss@hbz-nrw.de
 *
 */
class WebsiteMarcMetadata {

	/*
	 * HBZ-ID. Auch Aleph-ID, HT-Nr, TT-Nr genannt. Herkunft: MARC 001. Format:
	 * 2 Buchstaben + 9stellig numerisch
	 */
	private String hbzId = null;
	/*
	 * Erfassungsdatum des Datensatzes. Herkunft: MARC 008 Pos 0-5
	 * "date entered on file". Formatierung hier: YYYY-MM-DD
	 */
	private String dateEntered = null;
	/*
	 * URL (Internet-Adresse) der eingesammelten (oder einzusammelnden) Website.
	 * Herkunft: MARC 8564 $$u; aber KEINE URN und KEINE Edoweb3-ID (als URN),
	 * die ebenfalls in MARC 8564 $$u stehen können!
	 */
	private String url = null;
	/*
	 * Edoweb3-ID Herkunft: MARC 856 $$u (ist nicht immer vorhanden) Format
	 * hier: edoweb:<ID>, <ID> = numerisch.
	 */
	private String parentPid = null;
	/*
	 * URL_ID im Edoweb2-Webgatherer, zur eindeutigen Zuordnung der Altdaten zu
	 * den Edoweb3-Objekten. Herkunft: MARC 998 $$a
	 */
	private String urlId = null;

	public WebsiteMarcMetadata() {
		// leerer Kontruktor, Initialisierung der Variablen erfolgt direkt bei
		// deren Deklaration.
	}

	/*
	 * Getter- und Setter Methoden
	 */
	public void setHbzId(String hbzId) {
		this.hbzId = hbzId;
	}

	public String getHbzId() {
		return this.hbzId;
	}

	public void setDateEntered(String dateEntered) {
		this.dateEntered = dateEntered;
	}

	public String getDateEntered() {
		return this.dateEntered;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUrl() {
		return this.url;
	}

	public void setParentPid(String parentPid) {
		this.parentPid = parentPid;
	}

	public String getParentPid() {
		return this.parentPid;
	}

	public void setUrlId(String urlId) {
		this.urlId = urlId;
	}

	public String getUrlId() {
		return this.urlId;
	}

}
