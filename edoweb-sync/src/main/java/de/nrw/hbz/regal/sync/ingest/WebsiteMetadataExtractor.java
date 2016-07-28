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

import java.io.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
// import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import de.nrw.hbz.regal.sync.DigitoolDownloadConfiguration;
// import play.Logger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author I. Kuss kuss@hbz-nrw.de
 *
 */
class WebsiteMetadataExtractor {

	/**
	 * This is a class with a main()-method. The main()-method extracts metadata
	 * from previously downloaded objects. The metadata are written in the json
	 * format (javascript object notation). The fields in the metadata file are
	 * closely related to the fields in the regal-api, and, correspondingly in
	 * the elasticsearch-api. Ideally, the metadata file can be fed with only
	 * minimal modifications into the regal-api when an object is to be imported
	 * from the download-area (data coming from the heritage system) into the
	 * fedora reporitory via the regal-api.
	 * 
	 * Currently only websites are supported by the Metadata Extractor. For each
	 * website which has been downloaded in the format _tidy.zip the Metadata
	 * Extractor writes a metadata file. The metadata file is placed next to the
	 * directory in which the zip-file containing the crawled website resides.
	 * The metadata file is called <directory>.website.json, where
	 * <directory> is the directory in which the _tidy.zip-file resides. E.g.
	 * 6040001.website.json when there is a zip-file 6040001/webarchive_tidy.zip
	 * .
	 * 
	 * A (still-to-be-written) Ingester (for websites) then has to read all
	 * metadata files in the given download-directory and ingest their
	 * corresponding zip-files (or any derivatives thereof) along with the read
	 * metadata to the regal-api.
	 * 
	 */
	private static String edowebbase = null;
	private static String konkordanz = null;
	private static DocumentBuilderFactory factory = null;
	private static DocumentBuilder builder = null;
	private static WebsiteMarcMetadata wsmmd = null;
	private static WebsiteVersionMetadata wsvmd = null;
	private static WebsiteKonkordanz wsk = null;
	final static Logger logger = LoggerFactory.getLogger(WebsiteMetadataExtractor.class);

	public WebsiteMetadataExtractor() {
		// allg. Konstruktor
		// bruuche mer nit, it's a main()-Class
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		// the main()-method does all the job
		final Options options = new Options();
		options.addOption("d", "basedir", true,
				"Downloadverzeichnis mit den vom Downloader abgelegten Daten, z.B. /opt/regal/edowebbase");
		options.addOption("k", "konkordanz", true,
				"Konkordanz Edoweb2-Objekte zu Edoweb3-Objekte (für Websites), eine csv-Datei mit den Feldern URL_ID;HBZID;EDOWEB3_ID;URL; z.B. /opt/regal/heritrix-import/loaded/imported.edoweb-rlp.de.enriched.ids.csv");
		DigitoolDownloadConfiguration cmdlineOpts = new DigitoolDownloadConfiguration(args, options,
				WebsiteMetadataExtractor.class);
		try {
			if (!cmdlineOpts.hasOption("basedir")) {
				throw (new RuntimeException(
						"Fehlender Parameter! Bitte übergeben Sie ein Downloadverzeichnis mit der Option -d !"));
			}
			if (!cmdlineOpts.hasOption("konkordanz")) {
				throw (new RuntimeException(
						"Fehlender Parameter! Bitte übergeben Sie eine Konkordanz mit der Option -k !"));
			}
			edowebbase = cmdlineOpts.getOptionValue("basedir");
			logger.info("download-Basisverzeichnis: " + edowebbase);
			File baseDir = new File(edowebbase);
			if (!baseDir.isDirectory()) {
				throw (new RuntimeException(
						"Download-Basisverzeichnis " + edowebbase + " ist kein Verzeichnis !! Abbruch."));
			}
			konkordanz = cmdlineOpts.getOptionValue("konkordanz");
			logger.info("Konkordanz: " + konkordanz);
			File konkFile = new File(konkordanz);
			if (!konkFile.isFile()) {
				throw (new RuntimeException("Konkordanz " + konkordanz + " ist keine Datei !! Abbruch."));
			}
			wsk = new WebsiteKonkordanz(konkFile);
			factory = DocumentBuilderFactory.newInstance();
			builder = factory.newDocumentBuilder();

			/* Los geht's (Hauptverarbeitung) */
			for (File dir : baseDir.listFiles()) {
				if (!dir.isDirectory()) {
					continue;
				}
				logger.info("Öffne Verzeichnis: " + dir.getName());
				// Durchsuche das Verzeichnis zuerst nach MARC-Metadaden (es
				// muss welche geben)
				wsmmd = new WebsiteMarcMetadata();
				for (File file : dir.listFiles()) {
					if (!file.isFile()) {
						continue;
					}
					if (!file.getName().matches(".+.xml")) {
						continue;
					}
					// Suche Datei nach MARC-Metadaten ab ,lies diese ggfs. ein
					if (parseMarcMetadata(file) == true) {
						/* MARC-Metadaten gefunden, nicht mehr weiter suchen */
						break;
					}
				}
				if (wsmmd.getHbzId() == null) {
					logger.warn("Keine MARC-Metadaten gefunden !!");
				} else {
					logger.info("MARC-Metadaten zu hbz-ID " + wsmmd.getHbzId() + " gefunden und eingelesen.");
				}
				// Lies jetzt die Digitalen Entitäten der einzelnen Objekte aus
				for (File file : dir.listFiles()) {
					if (!file.isFile()) {
						continue;
					}
					if (!file.getName().matches(".+.xml")) {
						continue;
					}
					logger.info("found XML-Datei: " + file.getName());
					if (parseDigitalEntity(dir, file) == true) {

						/*
						 * Anreicherung der bisher nach WebsiteVersionMetadata
						 * eingelesenen Daten durch: a) feste Dateninhalte
						 * (Fix-Felder) b) andere, bereits vorhandene
						 * Datenfelder aus WebsiteVersionMetadata c) Inhalte von
						 * WebsiteMarcMetadata (MARC-Metadaten) d) meine
						 * Konkordanz Edoweb2.0-Objekte <=> Edoweb3.0-Objekte
						 * (für Websites)
						 */
						enrichWebsiteVersionMetadata();

						/*
						 * Ausgabe des Inhalts von WebsiteVersionMetadata in
						 * eine Datei vom Format .json. Die .json-Datei liegt
						 * neben der soeben geparsten XML-Datei.
						 */
						wsvmd.print(dir, file);

					}
				} // next file
			} // next dir
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace(System.err);
		}

	} // end of main

	/*
	 * Diese Methode parst ein einzelnes XML-File im Format <xb:digital_entity>
	 * aus. Die Ergebnisse werden in einer Instanz von WebsiteVersionMetadata
	 * hinterlegt. Falls eine Digitale Entität vom Type "WEBARCHIVE VIEW*"
	 * erfolgreich eingelesen wurde, wird "true" zurück gegeben, sonst "false".
	 */
	private static boolean parseDigitalEntity(File dir, File file) {
		try {
			Document doc = builder.parse(file);
			Element root = doc.getDocumentElement();
			if (!root.getTagName().equals("xb:digital_entity")) {
				logger.warn("XML-DOM root-Element von Datei " + file.getName() + " ist nicht <xb:digital_entity> !");
				logger.warn("XML-Datei " + file.getName() + " wird ignoriert.");
				return false;
			}
			wsvmd = new WebsiteVersionMetadata();
			wsvmd.getEdo2Data().setParentPid(dir.getName());
			wsvmd.getEdo2Data().setPid(root.getElementsByTagName("pid").item(0).getTextContent());
			logger.info("Pid=" + wsvmd.getEdo2Data().getPid());
			Element control = (Element) root.getElementsByTagName("control").item(0);
			wsvmd.getEdo2Data().setEntityType(control.getElementsByTagName("entity_type").item(0).getTextContent());
			if (!wsvmd.getEdo2Data().getEntityType().equals("WEBARCHIVE")) {
				logger.info("Objekt zur Pid ist kein Webarchiv - wird übergangen.");
				/*
				 * es werden nur Webarchive mit Metadaten versehen (keine OCR,
				 * INDEX oder COMPLEX-PIDs)
				 */
				return false; //
			}
			wsvmd.getEdo2Data().setUsageType(control.getElementsByTagName("usage_type").item(0).getTextContent());
			if (!wsvmd.getEdo2Data().getUsageType().matches("^VIEW.*$")) {
				logger.info("Objekt zur Pid ist kein anzeigbares Objekt (UsageType VIEW*) - wird übergangen.");
				/*
				 * es werden nur anzuzeigende Objekte mit Metadaten versehen
				 * (keine Archivobjekte (UsageType ARCHIVE))
				 */
				return false;
			}
			String label = control.getElementsByTagName("label").item(0).getTextContent();
			logger.info("Label=" + label);
			wsvmd.getEdo2Data().setLabel(label);
			String ingestId = control.getElementsByTagName("ingest_id").item(0).getTextContent();
			logger.info("INGEST_ID=" + ingestId);
			wsvmd.getEdo2Data().setIngestId(ingestId);
			wsvmd.getIsDescribedBy().setCreated(control.getElementsByTagName("creation_date").item(0).getTextContent());
			logger.info("creation_date=" + wsvmd.getIsDescribedBy().getCreated());
			wsvmd.getIsDescribedBy()
					.setModified(control.getElementsByTagName("modification_date").item(0).getTextContent());
			logger.info("modification_date=" + wsvmd.getIsDescribedBy().getModified());
			Element streamRef = (Element) root.getElementsByTagName("stream_ref").item(0);
			wsvmd.setLocalDir(edowebbase.replaceAll("/$", "").concat("/").concat(dir.getName()).concat("/")
					.concat(file.getName().replaceAll(".xml$", "")).concat("/")
					.concat(streamRef.getElementsByTagName("file_name").item(0).getTextContent()));
			logger.info("localDir=" + wsvmd.getLocalDir());
			wsvmd.getHasData().setFormat(streamRef.getElementsByTagName("mime_type").item(0).getTextContent());
			logger.info("mime_type=" + wsvmd.getHasData().getFormat());
			wsvmd.getHasData().setSize(streamRef.getElementsByTagName("file_size_bytes").item(0).getTextContent());
			logger.info("file_size_bytes=" + wsvmd.getHasData().getSize());
			return true;
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace(System.err);
		}
		return false;
	} // end of method parseDigitalEntity

	/*
	 * Diese Methode parst MARC-Metadaten aus einem XML-File des Inhaltsformats
	 * <xb:digital_entity>. Falls MARC Metadatden gefunden und erfolgreich
	 * eingelesen (geparst) wurden, liefert die Methode "true" zurück, ansonsten
	 * "false". Es werden nur MARC Metadaten eingelesen, die für
	 * Website-Versionen relevant sind. Diese werden in der aktuellen Instanz
	 * der Klassenvariablen wsmmd (WebsiteMarcMetadata) gespeichert.
	 */
	private static boolean parseMarcMetadata(File file) {
		try {
			Document doc = builder.parse(file);
			Element root = doc.getDocumentElement();
			if (!root.getTagName().equals("xb:digital_entity")) {
				logger.warn("XML-DOM root-Element von Datei " + file.getName() + " ist nicht <xb:digital_entity> !");
				logger.warn("XML-Datei " + file.getName() + " wird nicht auf MARC-Metadaten geparst.");
				return false;
			}
			Element mds = (Element) root.getElementsByTagName("mds").item(0);
			if (mds == null) {
				return false;
			}
			NodeList mdList = mds.getElementsByTagName("md");
			for (int s = 0; s < mdList.getLength(); s++) {
				Element md = (Element) mdList.item(s);
				if (!md.getElementsByTagName("name").item(0).getTextContent().equals("descriptive")) {
					continue;
				}
				if (!md.getElementsByTagName("type").item(0).getTextContent().equals("marc")) {
					continue;
				}
				Element value = (Element) md.getElementsByTagName("value").item(0);
				if (parseMarcMetadata(value) == true) {
					return true;
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace(System.err);
		}
		return false;
	} // end of method parseMarcMetadata(File)

	/*
	 * parst MARC-Metadaten aus einem DOM-Element <value>. Oberster Knoten muss
	 * <record> sein. Liefert "true" zurück, falls MARC Metadaten erfolgreich
	 * geparst wurden, ansonsten "false". Erfolgreich eingelesene MARC Metadaten
	 * werden in der Instanzvariablen wsmmd (WebsiteMarcMetadata) abgelegt.
	 */
	private static boolean parseMarcMetadata(Element value) {
		try {
			wsmmd = new WebsiteMarcMetadata(); // sicherheitshalber hier noch
												// einmal initialisieren
			Element record = (Element) value.getElementsByTagName("record").item(0);
			// 1. Lies Kontrollfelder
			NodeList controlfields = record.getElementsByTagName("controlfield");
			for (int i = 0; i < controlfields.getLength(); i++) {
				Element controlfield = (Element) controlfields.item(i);
				if (controlfield.getAttribute("tag").equals("001")) {
					wsmmd.setHbzId(controlfields.item(i).getTextContent());
					logger.info("hbz-ID: " + wsmmd.getHbzId());
				} else if (controlfield.getAttribute("tag").equals("008")) {
					wsmmd.setDateEntered(
							String.format("20%2s-%2s-%2s", controlfields.item(i).getTextContent().substring(0, 2),
									controlfields.item(i).getTextContent().substring(2, 4),
									controlfields.item(i).getTextContent().substring(4, 6)));
					logger.info("Erstelldatum: " + wsmmd.getDateEntered());
				}
			}
			// 2. Lies Datenfelder
			NodeList datafields = record.getElementsByTagName("datafield");
			for (int i = 0; i < datafields.getLength(); i++) {
				Element datafield = (Element) datafields.item(i);
				if (datafield.getAttribute("tag").equals("856") && datafield.getAttribute("ind1").equals("4")) {
					NodeList subfields = datafield.getElementsByTagName("subfield");
					for (int j = 0; j < subfields.getLength(); j++) {
						Element subfield = (Element) subfields.item(j);
						if (!subfield.getAttribute("code").equals("u")) {
							continue;
						}
						String url = subfields.item(j).getTextContent();
						if (url.matches("^urn.*$")) {
							// URN, wird hier nicht weiter verarbeitet
							continue;
						} else if (url.matches("^.*www.edoweb-rlp.de/resource/edoweb.*$")) {
							// Edoweb3-ID gefunden
							wsmmd.setParentPid(parseEdo3Url(url));
							logger.info("Edoweb3-ID: " + wsmmd.getParentPid());
						} else {
							/*
							 * Falls man bis hier durchkommt, wird angenommen,
							 * dass es sich um die URL handelt, die auch
							 * tatsächlich eingesammelt wurde, also die
							 * Internetadresse der Website.
							 */
							wsmmd.setUrl(url);
							logger.info("URL: " + url);
						}
					}
				} else if (datafield.getAttribute("tag").equals("998")) {
					NodeList subfields = datafield.getElementsByTagName("subfield");
					for (int j = 0; j < subfields.getLength(); j++) {
						Element subfield = (Element) subfields.item(j);
						if (!subfield.getAttribute("code").equals("a")) {
							continue;
						}
						wsmmd.setUrlId(parseUrlId(subfields.item(j).getTextContent()));
						logger.info("URL_ID: " + wsmmd.getUrlId());
					}
				}
			}
			// Erfolgskriterium definieren: mindestens eine HBZ-ID in gültigem
			// Format muss eingelesen worden sein
			if (wsmmd.getHbzId() != null) {
				if (wsmmd.getHbzId().matches("^[a-zA-Z][a-zA-Z][0-9]+$")) {
					return true;
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace(System.err);
		}
		return false;
	} // end of method parseMarcMetadata(Element)

	/*
	 * Diese Methode parst eine URL, die eine Edoweb3-ID enthält. Nur die
	 * Edoweb3-ID (Format edoweb:<numerisch>) wird zurückgegeben. Im Fehlerfalle
	 * wird der Nullwert zurückgegeben.
	 */
	private static String parseEdo3Url(String edo3Url) {
		Pattern pattern = Pattern.compile("^.*www.edoweb-rlp.de/resource/(edoweb:[0-9]+).*$");
		Matcher matcher = pattern.matcher(edo3Url);
		if (matcher.matches()) {
			return matcher.group(1);
		}
		pattern = Pattern.compile("^.*www.edoweb-rlp.de/resource/(edoweb%3A[0-9]+).*$");
		matcher = pattern.matcher(edo3Url);
		if (matcher.matches()) {
			return matcher.group(1).replaceAll("%3A", ":");
		}
		return (String) null;
	}

	/*
	 * Diese Methode parst den Inhalt von MARC 998 $$a auf eine URL_ID. Falls
	 * eine URL_ID gefunden wird, wird diese zurück gegeben. Format: String mit
	 * nur Ziffern als Inhalt. Falls nicht, wird der Nullwert zurück gegeben.
	 */
	private static String parseUrlId(String marc998) {
		Pattern pattern = Pattern.compile("^HBZURLID([0-9]+)$");
		Matcher matcher = pattern.matcher(marc998);
		if (matcher.matches()) {
			return matcher.group(1);
		}
		return (String) null;
	}

	/*
	 * Diese Methode reichert die bisher nach WebsiteVersionMetadata (also
	 * Metadaten für Websites in Regal/Fedora) eingelesenen Inhalte an. Dabei
	 * wird auf dort bereits vorhandene Inhalte, Inhalte von WebsiteMarcMetadata
	 * (also MARC-Metadaten) und meine Konkordanz Edoweb2.0-Objekte <=>
	 * Edoweb3.0-Objekte zurückgegriffen. Außerdem werden feste Inhalte
	 * (Fix-Felder) hinzugefügt.
	 */
	private static void enrichWebsiteVersionMetadata() {
		try {
			/* Fix-Wert, streng genommen aus accessrights auszulesen */
			wsvmd.setAccessScheme("public");
			wsvmd.setCatalogId(wsmmd.getHbzId());
			wsvmd.setContentType("version"); // Fix-Wert

			// will "fileLabel" für Edo3 generieren.
			// 1. Versuch: aus <control><label>
			String label = wsvmd.getEdo2Data().getLabel();
			Pattern pattern = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+) (\\d+):(\\d+):(\\d+)$");
			Matcher matcher = pattern.matcher(label);
			if (matcher.matches()) {
				int tag = Integer.parseInt(matcher.group(1));
				int monat = Integer.parseInt(matcher.group(2));
				int jahr = Integer.parseInt(matcher.group(3));
				wsvmd.getHasData().setFileLabel(String.format("%4d-%02d-%02d", jahr, monat, tag));
			} else if (wsmmd.getDateEntered() != null) {
				// 2. Versuch: aus MARC 008
				wsvmd.getHasData().setFileLabel(wsmmd.getDateEntered());
			} else {
				// letzter Versuch: aus <control><creation_date>
				wsvmd.getHasData().setFileLabel(wsvmd.getIsDescribedBy().getCreated().substring(0, 10));
			}
			logger.info("fileLabel: " + wsvmd.getHasData().getFileLabel());

			// Ermittlung der URL_ID (brauche ich als Hauptschlüssel zu meiner
			// Konkordanz)
			// 1. Versuch: aus MARC 998 $$a
			if (wsmmd.getUrlId() != null) {
				wsvmd.getEdo2Data().setUrlId(wsmmd.getUrlId());
			} else {
				// 2. Versuch: aus <control><ingest_id>
				String ingestId = wsvmd.getEdo2Data().getIngestId();
				pattern = Pattern.compile("^rlb01_url_([0-9]+)_ws_.*$");
				matcher = pattern.matcher(ingestId);
				if (matcher.matches()) {
					wsvmd.getEdo2Data().setUrlId(matcher.group(1));
					logger.info("URL_ID: " + wsvmd.getEdo2Data().getUrlId());
				} else {
					// 3. Versuch: aus der Konkordanz über HTNr
					for (HashMap<String, String> lineHash : wsk.getUrlIdHash().values()) {
						if (lineHash.get("HBZID").equals(wsvmd.getCatalogId())) {
							wsvmd.getEdo2Data().setUrlId(lineHash.get("URL_ID"));
							logger.info("URL_ID= " + lineHash.get("URL_ID"));
							break;
						}
					}
				}
			}

			// Ermittlung der URL
			// 1. Versuch: aus meiner Konkordanz über URL_ID
			String urlId = wsvmd.getEdo2Data().getUrlId();
			if (urlId != null && wsk.getUrlIdHash().containsKey(urlId)) {
				wsvmd.getIsDescribedBy().setInputFrom(wsk.getUrlIdHash().get(urlId).get("URL"));
				logger.info("URL aus Konkordanz genommen: " + wsk.getUrlIdHash().get(urlId).get("URL"));
			} else if (wsmmd.getUrl() != null) {
				// 2. Versuch: aus MARC 8564 $$u
				wsvmd.getIsDescribedBy().setInputFrom(wsmmd.getUrl());
			} else {
				// 3. Versuch: aus der Konkordanz über HTNr
				for (HashMap<String, String> lineHash : wsk.getUrlIdHash().values()) {
					if (lineHash.get("HBZID").equals(wsvmd.getCatalogId())) {
						wsvmd.getIsDescribedBy().setInputFrom(lineHash.get("URL"));
						logger.info("URL aus Konkordanz über HTNr : " + lineHash.get("URL"));
						break;
					}
				}
			}

			wsvmd.getIsDescribedBy().setCreatedBy("webgatherer"); // fester Wert
			// Object-Zeitstempel generieren
			// 1. Versuch: aus <control><label>

			pattern = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).(\\d+):(\\d+):(\\d+)$");
			matcher = pattern.matcher(wsvmd.getEdo2Data().getLabel());
			if (matcher.matches()) {
				int tag = Integer.parseInt(matcher.group(1));
				int monat = Integer.parseInt(matcher.group(2));
				int jahr = Integer.parseInt(matcher.group(3));
				int stunde = Integer.parseInt(matcher.group(4));
				int minute = Integer.parseInt(matcher.group(5));
				int sekunde = Integer.parseInt(matcher.group(6));
				wsvmd.getIsDescribedBy().setObjectTimestamp(
						String.format("%4d-%02d-%02d %02d:%02d:%02d", jahr, monat, tag, stunde, minute, sekunde));
			} else if (wsmmd.getDateEntered() != null) {
				// 2. Versuch: aus MARC 008 (nur Datum)
				wsvmd.getIsDescribedBy().setObjectTimestamp(wsmmd.getDateEntered());
			} else {
				// letzter Versuch: aus <control><creation_date>
				wsvmd.getIsDescribedBy().setObjectTimestamp(wsvmd.getIsDescribedBy().getCreated());
			}
			logger.info("objectTimestamp: " + wsvmd.getIsDescribedBy().getObjectTimestamp());

			// Hole Edo3-ID ("parentPid") - falls vorhanden
			// 1. Versuch: aus MARC 8564 $$u
			if (wsmmd.getParentPid() != null) {
				wsvmd.setParentPid(wsmmd.getParentPid());
			} else {
				// 2. Versuch: aus meiner Konkordanz über URL_ID
				urlId = wsvmd.getEdo2Data().getUrlId();
				if (urlId != null && wsk.getUrlIdHash().containsKey(urlId)) {
					wsvmd.setParentPid(wsk.getUrlIdHash().get(urlId).get("EDOWEB3_ID"));
					logger.info("EDO3_ID aus Konkordanz genommen: " + wsvmd.getParentPid());
				}
			}
			wsvmd.setPublishScheme("fix");
			wsvmd.setTitle(wsvmd.getHasData().getFileLabel());

		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace(System.err);
		}
	} // end of method enrichWebsiteVersionMetadata

} // end of class WebsiteMetadataExtractor
