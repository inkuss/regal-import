/*
 * Copyright 2012 hbz NRW (http://www.hbz-nrw.de/)
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
package de.nrw.hbz.regal.sync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.nrw.hbz.regal.DigitoolPidStrategy;
import de.nrw.hbz.regal.PIDReporter;
import de.nrw.hbz.regal.sync.extern.DigitalEntity;
import de.nrw.hbz.regal.sync.extern.DigitalEntityBuilderInterface;
import de.nrw.hbz.regal.sync.ingest.DownloaderInterface;
import de.nrw.hbz.regal.sync.ingest.IngestInterface;
import de.nrw.hbz.regal.sync.ingest.KeystoreConf;

/**
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 */
public class Syncer {

    @SuppressWarnings({ "serial", "javadoc" })
    public class ReadFileException extends RuntimeException {
	public ReadFileException(Throwable cause) {
	    super(cause);
	}
    }

    @SuppressWarnings({ "serial", "javadoc" })
    public class CloseReaderException extends RuntimeException {
	public CloseReaderException(Throwable cause) {
	    super(cause);
	}
    }

    @SuppressWarnings({ "serial", "javadoc" })
    public class IngestItemException extends RuntimeException {
	public IngestItemException(Throwable cause) {
	    super(cause);
	}
    }

    final static Logger logger = LoggerFactory.getLogger(Syncer.class);

    private PIDReporter harvester;
    private IngestInterface ingester;
    private DownloaderInterface downloader;
    private DigitalEntityBuilderInterface builder;
    private String mode;
    private String user;
    private String password;
    private String dtl;
    private String cache;
    private String oai;
    private String set;
    private String timestamp;
    // private String fedoraBase;
    private String host;
    private String pidListFile;
    private Options options;
    private String namespace;
    KeystoreConf kconf = null;

    /**
     * @param ingester
     *            the ingester is used to pass data to the archive
     * @param downloader
     *            the downloader is used to download data from a foreign system
     * @param builder
     *            the builder assembles DigitalEntities from the downloaded data
     *            and passes it to the ingester.
     */
    public Syncer(IngestInterface ingester, DownloaderInterface downloader,
	    DigitalEntityBuilderInterface builder) {
	this.ingester = ingester;
	this.downloader = downloader;
	this.builder = builder;
	options = new Options();

	options.addOption("?", "help", false, "Print usage information");
	options.addOption(
		"m",
		"mode",
		true,
		"Specify mode: \n "
			+ "INIT: All PIDs will be downloaded. All pids will be updated or created.\n"
			+ "SYNC: Modified or new PIDs will be downloaded and updated or created\n "
			+ "CONT: All PIDs that aren't already downloaded will be downloaded and created or updated\n"
			+ "UPDT: In accordance to the timestamp all modified PIDs will be reingested"
			+ "PIDL: Use this mode in combination with -list to provide a file with a newline separated pidlist"
			+ "DELE: Use this mode in combination with -list to provide a file with a newline separated pidlist");
	options.addOption("u", "user", true, "Specify username");
	options.addOption("p", "password", true, "Specify password");
	options.addOption("dtl", "dtl", true, "Specify digitool url");
	options.addOption("cache", "cache", true, "Specify local directory");
	options.addOption("oai", "oai", true, "Specify the OAI-PMH endpoint");
	Option setOption = new Option("set", "set", true,
		"Specify an OAI setSpec");
	setOption.setValueSeparator(',');
	setOption.setRequired(false);
	setOption.setOptionalArg(true);
	options.addOption(setOption);
	options.addOption("timestamp", "timestamp", true,
		"Specify a local file e.g. .oaitimestamp");
	options.addOption("fedoraBase", "fedoraBase", true,
		"The Fedora Baseurl");
	options.addOption("host", "host", true, "The Fedora Baseurl");
	options.addOption("namespace", "namespace", true,
		"The namespace to operate on.");
	options.addOption(
		"list",
		"list",
		true,
		"Path to a file with a newline separated pidlist. Only needed in combination with mode PIDL and DELE.");
	options.addOption("keystoreLocation", "keystoreLocation", true,
		"Specify a keystore for ssl");
	options.addOption("keystorePassword", "keystorePassword", true,
		"Specify a keystore password for ssl");
    }

    /**
     * @param args
     *            usage: -oai,--oai &lt;arg&gt; Specify the OAI-PMH endpoint
     *            -m,--mode &lt;arg&gt; Specify mode: INIT: All PIDs will be
     *            downloaded. All pids will be updated or created. SYNC:
     *            Modified or new PIDs will be downloaded and updated or created
     *            CONT: All PIDs that aren't already downloaded will be
     *            downloaded and created or updated UPDT: In accordance to the
     *            timestamp all modified PIDs will be reingestedPIDL: Use this
     *            mode in combination with -list to provide a file with a
     *            newline separated pidlistMODL: Recreates only the Content
     *            modelDELE: Use this mode in combination with -list to provide
     *            a file with a newline separated pidlist -?,--help Print usage
     *            information -cache,--cache &lt;arg&gt; Specify local directory
     *            -dtl,--dtl &lt;arg&gt; Specify digitool url
     *            -fedoraBase,--fedoraBase &lt;arg&gt; The Fedora Baseurl
     *            -host,--host &lt;arg&gt; The Fedora Baseurl -list,--list
     *            &lt;arg&gt; Path to a file with a newline separated pidlist.
     *            Only needed in combination with mode PIDL and DELE.
     *            -namespace,--namespace &lt;arg&gt; The namespace to operate
     *            on. -p,--password &lt;arg&gt; Specify password -set,--set
     *            &lt;arg&gt; Specify an OAI setSpec -timestamp,--timestamp
     *            &lt;arg&gt; Specify a local file e.g. .oaitimestamp -u,--user
     *            &lt;arg&gt; Specify username
     */
    public void main(String[] args) {
	init(args);
	run();
    }

    /**
     * @param args
     *            main args
     */
    public void init(String[] args) {

	DigitoolDownloadConfiguration config = new DigitoolDownloadConfiguration(
		args, options, Syncer.class);

	if (config.hasOption("help") | !config.hasOption("mode")
		| !config.hasOption("user") | !config.hasOption("password")
		| !config.hasOption("dtl") | !config.hasOption("cache")
		| !config.hasOption("oai") | !config.hasOption("timestamp")
		| !config.hasOption("fedoraBase") | !config.hasOption("host")
		| !config.hasOption("namespace"))

	{
	    showHelp(options);
	    return;
	}

	mode = config.getOptionValue("mode");
	user = config.getOptionValue("user");
	password = config.getOptionValue("password");
	dtl = config.getOptionValue("dtl");
	cache = config.getOptionValue("cache");
	oai = config.getOptionValue("oai");
	set = config.getOptionValue("set");
	timestamp = config.getOptionValue("timestamp");
	// fedoraBase = config.getOptionValue("fedoraBase");
	host = config.getOptionValue("host");
	namespace = config.getOptionValue("namespace");
	pidListFile = null;
	if (config.hasOption("list")) {
	    pidListFile = config.getOptionValue("list");
	}

	harvester = new de.nrw.hbz.regal.PIDReporter(oai, timestamp);
	downloader.init(dtl, cache);
	KeystoreConf kconf = new KeystoreConf();
	kconf.location = config.getOptionValue("keystoreLocation");
	kconf.password = config.getOptionValue("keystorePassword");
	ingester.init(host, user, password, namespace, kconf);
    }

    private void showHelp(Options opts) {
	HelpFormatter help = new HelpFormatter();
	help.printHelp(" ", opts);
    }

    private void run() {
	if (mode.compareTo("INIT") == 0) {
	    init(set);
	} else if (mode.compareTo("SYNC") == 0) {
	    sync(set);
	} else if (mode.compareTo("DWNL") == 0) {
		dwnl(set);
	} else if (mode.compareTo("CONT") == 0) {
	    cont(set);
	} else if (mode.compareTo("UPDT") == 0) {
	    updt(set);
	} else if (mode.compareTo("PIDL") == 0) {

	    pidl(pidListFile);

	} else if (mode.compareTo("DELE") == 0) {

	    dele(pidListFile);

	} else if (mode.compareTo("TEST") == 0) {
	    ingester.test();
	}

    }

    /*
     * "INIT: All PIDs will be downloaded. All pids will be updated or created.\n"
     */
    void init(String sets) {
	boolean harvestFromScratch = true;
	boolean forceDownload = true;

	List<String> pids = harvester.harvest(sets, harvestFromScratch,
		new DigitoolPidStrategy(), "oai_dc");
	logger.info("Verarbeite " + pids.size() + " Dateneinheiten.");

	int size = pids.size();
	for (int i = 0; i < size; i++) {
	    try {
		logger.info((i + 1) + " / " + size);
		String pid = pids.get(i);
		String baseDir = downloader.download(pid, forceDownload);
		logger.info("\tBuild Bean \t" + pid);

		if (!downloader.hasUpdated()) {
		    logger.info("New Files Available: Start Ingest!");
		    DigitalEntity dtlBean = builder.build(baseDir, pids.get(i));

		    ingester.ingest(dtlBean);
		    dtlBean = null;
		} else if (downloader.hasUpdated()) {
		    logger.info("Update Files!");
		    DigitalEntity dtlBean = builder.build(baseDir, pids.get(i));
		    ingester.ingest(dtlBean);
		    dtlBean = null;
		}

	    } catch (Exception e) {
		logger.error("", e);

	    }
	}

    }

    /*
     * +
     * "SYNC: Modified or new PIDs will be downloaded and updated or created\n "
     */
    void sync(String sets) {
	boolean harvestFromScratch = false;
	boolean forceDownload = true;

	List<String> pids = harvester.harvest(sets, harvestFromScratch,
		new DigitoolPidStrategy(), "oai_dc");
	logger.info("Verarbeite " + pids.size() + " Dateneinheiten.");

	int size = pids.size();
	for (int i = 0; i < size; i++) {
	    try {
		logger.info((i + 1) + " / " + size);
		String pid = pids.get(i);
		String baseDir = downloader.download(pid, forceDownload);
		logger.info("\tBuild Bean \t" + pid);

		if (!downloader.hasUpdated()) {
		    logger.info("New Files Available: Start Ingest!");
		    DigitalEntity dtlBean = builder.build(baseDir, pids.get(i));

		    ingester.ingest(dtlBean);
		    dtlBean = null;
		} else if (downloader.hasUpdated()) {
		    logger.info("Update Files!");
		    DigitalEntity dtlBean = builder.build(baseDir, pids.get(i));
		    ingester.update(dtlBean);
		    dtlBean = null;
		}

	    } catch (Exception e) {
		logger.error(e.toString(), e);
	    }
	}

    }
    
    /*
     * +
     * "DWNL: Harvests all Websites from timestamp till now to cache\n"
     */
    void dwnl(String sets) {
    	boolean harvestFromScratch = false;
    	boolean forceDownload = true;
    	
    	List<String> pids = harvester.harvest(sets, harvestFromScratch, new DigitoolPidStrategy(), "oai_dc");
    	logger.info("Verarbeite " + pids.size() + " Dateneinheiten.");
    	
    	int size = pids.size();
    	for(int i = 0; i < size; i++) {
    		try {
    			logger.info((i+1)+"/"+size);
    			String pid = pids.get(i);
    			String baseDir = downloader.download(pid, forceDownload);
    			logger.info("Download "+pid+"to "+baseDir);
    		} catch (Exception e) {
    			logger.error(e.toString(), e);
    		}
    	}
    }

    /*
     * +
     * "CONT: All PIDs that aren't already downloaded will be downloaded and created or updated\n"
     */
    void cont(String sets) {
	boolean harvestFromScratch = true;
	boolean forceDownload = false;
	List<String> pids = harvester.harvest(sets, harvestFromScratch,
		new DigitoolPidStrategy(), "oai_dc");
	logger.info("Verarbeite " + pids.size() + " Dateneinheiten.");
	int size = pids.size();
	for (int i = 0; i < size; i++) {
	    try {
		logger.info((i + 1) + " / " + size);
		String pid = pids.get(i);
		String baseDir = downloader.download(pid, forceDownload);
		logger.info("\tBuild Bean \t" + pid);

		if (!downloader.hasUpdated() && downloader.hasDownloaded()) {
		    logger.info("New Files Available: Start Ingest!");
		    DigitalEntity dtlBean = builder.build(baseDir, pids.get(i));

		    ingester.ingest(dtlBean);
		    dtlBean = null;
		}

	    } catch (Exception e) {
		logger.error(e.toString(), e);
	    }
	}

    }

    /*
     * +
     * "UPDT: in accordance to the timestamp all modified PIDs will be reingested"
     */
    void updt(String sets) {
	boolean harvestFromScratch = false;
	boolean forceDownload = false;

	List<String> pids = harvester.harvest(sets, harvestFromScratch,
		new DigitoolPidStrategy(), "oai_dc");
	logger.info("Verarbeite " + pids.size() + " Dateneinheiten.");

	int size = pids.size();
	for (int i = 0; i < size; i++) {
	    try {
		logger.info((i + 1) + " / " + size);
		String pid = pids.get(i);
		String baseDir = downloader.download(pid, forceDownload);
		logger.info("\tBuild Bean \t" + pid);

		logger.info("New Files Available: Start Ingest!");
		DigitalEntity dtlBean = builder.build(baseDir, pid);
		ingester.delete(pid);
		ingester.ingest(dtlBean);
		dtlBean = null;

	    } catch (Exception e) {
		logger.error(e.toString(), e);
	    }
	}
    }

    void pidl(String pidFile) {
	Vector<String> pids;

	pids = readPidlist(pidFile);
	int size = pids.size();
	for (int i = 0; i < size; i++) {
	    try {
		logger.info((i + 1) + " / " + size);
		String pid = pids.get(i);

		String baseDir = downloader.download(pid, false);

		if (!downloader.hasUpdated()) {

		    DigitalEntity dtlBean = builder.build(baseDir, pids.get(i));

		    ingester.ingest(dtlBean);
		    dtlBean = null;
		    logger.info((i + 1) + "/" + size + " " + pid
			    + " has been processed!\n");
		} else if (downloader.hasUpdated()) {

		    DigitalEntity dtlBean = builder.build(baseDir, pids.get(i));
		    ingester.ingest(dtlBean);
		    dtlBean = null;
		    logger.info((i + 1) + "/" + size + " " + pid
			    + " has been updated!\n");
		}

	    } catch (Exception e) {
		logger.error(e.toString(), e);
	    }
	}

    }

    void dele(String pidFile) {
	Vector<String> pids;
	pids = readPidlist(pidFile);
	int size = pids.size();
	for (int i = 0; i < size; i++) {
	    logger.info((i + 1) + " / " + size);
	    String pid = pids.get(i);
	    ingester.delete(pid);
	    logger.info((i + 1) + "/" + size + " " + pid + " deleted!\n");
	}
    }

    private Vector<String> readPidlist(String pidFile) {
	File file = new File(pidFile);
	Vector<String> result = new Vector<String>();
	BufferedReader reader = null;
	String str = null;
	try {
	    reader = new BufferedReader(new FileReader(file));
	    while ((str = reader.readLine()) != null) {
		result.add(str);
	    }
	} catch (IOException e) {
	    throw new ReadFileException(e);
	} finally {
	    if (reader != null)
		try {
		    reader.close();
		} catch (IOException e) {
		    throw new CloseReaderException(e);
		}

	}
	return result;
    }

}
