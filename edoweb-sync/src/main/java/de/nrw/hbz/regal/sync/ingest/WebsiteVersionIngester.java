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
import java.util.Arrays;
import org.apache.commons.cli.Options;
import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.commons.io.FileUtils;
// import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.core.ZipFile;
import actions.Create;
// import actions.Create.WebgathererTooBusyException;
import actions.Read;
import models.Node;
import de.nrw.hbz.regal.sync.DigitoolDownloadConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author I. Kuss, kuss@hbz-nrw.de
 *
 */
class WebsiteVersionIngester {

	private static String edowebsites = null;
	private static String htdocs = null;
	private static String port = null;
	private static WebsiteVersionMetadata wsvmd = null;
	final static Logger logger = LoggerFactory.getLogger(WebsiteMetadataExtractor.class);

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		final Options options = new Options();
		options.addOption("d", "basedir", true,
				"Downloadverzeichnis mit den vom Downloader abgelegten Daten inkl. Metadaten in *.website.json, z.B. /opt/regal/edowebsites");
		options.addOption("h", "htdocs", true,
				"Rootverzeichnis des lokalen Servers, unter dem die zip-Archive ausgepackt werden sollen, z.B. /srv/www/htdocs");
		options.addOption("p", "port", true,
				"Port des lokalen Servers, unter dem die zip-Archive ausgepackt werden sollen, z.B. 90");
		DigitoolDownloadConfiguration cmdlineOpts = new DigitoolDownloadConfiguration(args, options,
				WebsiteMetadataExtractor.class);
		try {
			if (!cmdlineOpts.hasOption("basedir")) {
				throw (new RuntimeException(
						"Fehlender Parameter! Bitte übergeben Sie ein Downloadverzeichnis mit der Option -d !"));
			}
			if (!cmdlineOpts.hasOption("htdocs")) {
				throw (new RuntimeException(
						"Fehlender Parameter! Bitte übergeben Sie das Rootverzeichnis des lokalen Servers mit der Option -h !"));
			}
			if (!cmdlineOpts.hasOption("port")) {
				throw (new RuntimeException(
						"Fehlender Parameter! Bitte übergeben Sie den Port des lokalen Servers mit der Option -p !"));
			}
			edowebsites = cmdlineOpts.getOptionValue("basedir");
			htdocs = cmdlineOpts.getOptionValue("htdocs");
			htdocs = htdocs.replaceAll("/$", "");
			port = cmdlineOpts.getOptionValue("port");
			logger.info("download-Basisverzeichnis: " + edowebsites);
			logger.info("Server-Rootverzeichnis: " + htdocs);
			logger.info("Server-Port: " + port);
			File baseDir = new File(edowebsites);
			if (!baseDir.isDirectory()) {
				throw (new RuntimeException(
						"Download-Basisverzeichnis " + edowebsites + " ist kein Verzeichnis !! Abbruch."));
			}
			/* Los geht's (Hauptverarbeitung) */
			File[] dirs = baseDir.listFiles();
			Arrays.sort(dirs, NameFileComparator.NAME_COMPARATOR);
			for (File dir : dirs) {
				if (!dir.isDirectory()) {
					continue;
				}
				logger.info("Öffne Verzeichnis: " + dir.getName());
				/* Dateien *.website.json lesen */
				for (File file : dir.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File file, String name) {
						return name.endsWith(".website.json");
					}
				})) {
					logger.info("Found website version metadata: " + file.getName());
					processWebsite(dir, file);
				} // next file
			} // next dir

		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace(System.err);
		}

	} // end of main method

	/*
	 * bearbeitet eine einzelne Datei *.website.json bzw. ein einzelnes
	 * Webarchiv
	 */
	private static void processWebsite(File dir, File file) throws RuntimeException {
		try {
			// Einlesen der Metadaten im Format JSON
			wsvmd = new WebsiteVersionMetadata(file);
			wsvmd.read();
			if (wsvmd.getParentPid() == null) {
				// es muss zunächst ein Edo3-Objekt vom Typ "website" angelegt
				// werden
				// 1. Ein Regal-Objekt vom Typ "website" anlegen
				// ToDo KS20160801
				return;
			}
			logger.info("Website Version hat schon eine parentPid: " + wsvmd.getParentPid());
			// 2. Auspacken des tidy.zip unter Server-Rootpath
			File versionUnzipped = new File(htdocs + "/" + wsvmd.getEdo2Data().getPid());
			if (versionUnzipped.exists()) {
				FileUtils.deleteDirectory(versionUnzipped);
			}
			versionUnzipped.mkdir();
			ZipFile zipFile = new ZipFile(wsvmd.getLocalDir());
			zipFile.extractAll(versionUnzipped.getAbsolutePath());

			// hier weiter KS20160805
			// 3. Crawl der Site anstoßen
			// hier so was ähnliches aufrufen wie
			Node n = new Read().readNode(wsvmd.getParentPid());
			// jetzt die URL temporär auf die lokale URL ändern !
			Create create = new Create();
			// Node webpageVersion = create.createWebpageVersion(n);
			// ^ Das gehtso nicht, weil "createWebpageVersion" in einer
			// PlayFramework-Umgebung
			// liegt, wir sind aber in einem Maven-Projekt.
			// => Stattdessen Nutzung eines API-Endpoints, also Aufruf über HTTP
			// Etwa so: POST .../resource/edoweb:<PID>/createVersion
			// mit curl (oder java-Äquivalent) muss ein JSON-Objekt (der Node /
			// die Website-Conf.)
			// mit dem POST übergeben werden !
			// siehe controllers.Resource.createVersion(pid)

			// siehe auch Paket de.nrw.hbz.regal.sync.ingest, Klasse WebClient.

			// nach erfolgreichem Crawl => dies wird asynchron erfolgen müssen,
			// nicht hier !
			// in einem zweiten (cron-)Job, der aufräumt :
			// 4. Nachbearbeitung des Crawls und Aufräumen (ausgepacktes zip
			// wieder löschen)
			// * 4.1 das Crawl-Datum ändern ; dieses aus den website.json-Daten
			// holen ("label")
			// * 4.2 die temporäre URL wieder zurück ändern auf die
			// ursprüngliche
			// URL
			// * ggfs. noch weitere Metadaten aus dem JSON in die webpageVersion
			// übernehmen
			// * 4.3 schließlich das ausgeüackte zip-Archiv wieder entfernen !

			/*
			 * weitere Gedanken und Ideen (Kuss, Schnasse 05.08.) : -
			 * DATENVOLUMEN der zu importierenden Websites beträgt 380 GB. Diese
			 * müssen zusätzlich noch entpackt und erneut gecrawlt werden, d.h.
			 * es sollte mehr als 3x so viel Plattenplatz zur Verfügung stehen
			 * am besten 1,5 TB ! => - Auslagerung aller Heritrix-Aktivitäten
			 * auf einen separaten Rechner ! -- Auslagerung von
			 * /opt/regal/heritrix-import/ in ein anzumountendes Verzeichnis (an
			 * Regal-Server anmounten).
			 */

			/*
			 * weitere Gedanken Kuss / Schnasse (05.08.): - LAUFZEIT. Es müssen
			 * 2161 Websites (Regal-Objekte vom Typ "version") angelegt werden.
			 * Wenn man, so wie bis jetzt, nur max. 5 neue Crawls pro Nacht
			 * zulässt, dauert das (mit Ausfallzeiten) 1,5 Jahre ! => Es müssen
			 * 40-50 Sites pro Nacht angestartet werden, damit der ganze Import
			 * innerhalb von 2 Monaten durchlaufen kann ! Außerdem muss während
			 * dieser Zeit der Import ständig überwacht werden (Log-Files der
			 * cronjobs überwachen, bei Fehlern eingreifen).
			 */

			// } catch (WebgathererTooBusyException e) {
			// logger.error("Webgatherer stopped! Heritrix is too busy.");
			// e.printStackTrace(System.err);
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace(System.err);
		}

		throw new RuntimeException("Abbruch nach der ersten bearbeitetem Site (Testmodus!)!!");
	}

}
