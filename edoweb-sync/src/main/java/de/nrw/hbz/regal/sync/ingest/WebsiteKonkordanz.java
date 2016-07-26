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
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class collects and administers all data from the "Konkordanz" (mapping)
 * of Edoweb2.0-objects to Edoweb3.0-objects as far as websites are concerned.
 * This class contains methods to read the Konkordanz (from file) and to
 * retrieve values from the Konkordanz.
 * 
 * 
 * @author I. Kuss, hbz, kuss@hbz-nrw.de
 *
 */
class WebsiteKonkordanz {

	private File konkFile = null;
	private HashMap<String, HashMap<String, String>> urlIdHash = null;
	final static Logger logger = LoggerFactory.getLogger(WebsiteKonkordanz.class);

	/**
	 * der Konstruktor
	 */
	public WebsiteKonkordanz(File konkFile) {
		this.konkFile = konkFile;
		readKonkFile();
	}

	public HashMap<String, HashMap<String, String>> getUrlIdHash() {
		return this.urlIdHash;
	}

	/*
	 * liest die Konkordanz ein
	 */
	private void readKonkFile() {
		FileReader reader = null;
		BufferedReader buf = null;
		String[] kopf = null;
		String[] felder = null;
		HashMap<String, String> lineHash = null;
		urlIdHash = new HashMap<String, HashMap<String, String>>();
		int anzZeilen = 0;
		logger.info("Lese Konkordanz ein.");
		try {
			reader = new FileReader(konkFile);
			buf = new BufferedReader(reader);
			String line = null;
			while ((line = buf.readLine()) != null) {
				// logger.info("Zeile " + line + " eingelesen.");
				if (line.matches("^\\^.*$")) {
					// Überschriftzeile
					kopf = line.substring(1).split(";");
					logger.info("Überschriftzeile mit " + kopf.length + " Feldern gefunden");
					continue;
				}
				if (line.matches("^#")) {
					// Kommentarzeile
					continue;
				}
				felder = line.split(";");
				lineHash = new HashMap<String, String>();
				for (int i = 0; i < kopf.length; i++) {
					lineHash.put(kopf[i], felder[i]);
				}
				urlIdHash.put(lineHash.get("URL_ID"), lineHash);
				anzZeilen++;
				/*
				 * logger.info("Hash zur URL_ID " + lineHash.get("URL_ID") +
				 * " mit HBZID=" + lineHash.get("HBZID") + ", EDOWEB3_ID=" +
				 * lineHash.get("EDOWEB3_ID") + ", URL=" + lineHash.get("URL") +
				 * " gespeichert.");
				 */
			}
			logger.info(anzZeilen + " Konkordanzzeilen eingelesen.");
		} catch (IOException e) {
			logger.error(e.getMessage());
			e.printStackTrace(System.err);
		} finally {
			try {
				buf.close();
				reader.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
				e.printStackTrace(System.err);
			}
		}
	}

}
