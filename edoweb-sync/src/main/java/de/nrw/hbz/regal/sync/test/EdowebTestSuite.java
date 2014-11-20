package de.nrw.hbz.regal.sync.test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import models.DublinCoreData;
import models.RegalObject;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.nrw.hbz.regal.sync.ingest.Webclient;

@SuppressWarnings("javadoc")
public class EdowebTestSuite {
    final static Logger logger = LoggerFactory.getLogger(EdowebTestSuite.class);
    Properties properties = new Properties();
    Webclient client = null;
    String namespace = null;
    String user = null;
    String password = null;
    String host = null;

    public EdowebTestSuite() {
	initProperties();
	client = new Webclient(namespace, user, password, "http://" + host);
    }

    private void initProperties() {
	try {
	    properties.load(getClassLoader().getResourceAsStream(
		    "test.properties"));
	    namespace = getProperty("namespace");
	    user = getProperty("user");
	    password = getProperty("password");
	    host = getProperty("host");
	} catch (Exception e) {
	    throw new RuntimeException("", e);
	}
    }

    private ClassLoader getClassLoader() {
	return Thread.currentThread().getContextClassLoader();
    }

    private String getProperty(String key) {
	return properties.getProperty(key);
    }

    private InputStream str2stream(String str) {
	try {
	    return new ByteArrayInputStream(str.getBytes("utf-8"));
	} catch (UnsupportedEncodingException e) {
	    throw new RuntimeException("", e);
	}
    }

    private RegalObject read(InputStream in) throws Exception {
	ObjectMapper mapper = new ObjectMapper();
	mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
		false);
	RegalObject o = mapper.readValue(in, RegalObject.class);
	return o;
    }

    private RegalObject read(String id) {
	try {
	    return read(getClassLoader().getResourceAsStream(
		    "examples/" + id + "/create.json"));
	} catch (Exception e) {
	    throw new RuntimeException("Can`t read in " + id, e);
	}
    }

    public void createObject(String id) {
	try {
	    RegalObject input = read(id);
	    client.createResource(input, namespace + ":" + id);
	    RegalObject output = read(str2stream(client.readResource(namespace
		    + ":" + id)));
	    Assert.assertEquals(input, output);
	    findData(id);
	} catch (Exception e) {
	    throw new RuntimeException("Can`t read in " + id, e);
	}
    }

    private void findData(String id) {
	try {
	    client.updateData(namespace + ":" + id, new File(getClassLoader()
		    .getResource("examples/" + id + "/data.pdf").getFile()),
		    "application/pdf");
	} catch (Exception e) {
	    logger.info(id + " no datastream found");
	}
    }

    public void updateObject(String id) {
	RegalObject input = read(id);
	DublinCoreData dc = new DublinCoreData();
	dc.addIdentifier(input.getIsDescribedBy().getLegacyId());
	client.addIdentifier(dc, id, "test");
	client.lobidify("test:" + id);
    }

    public void deleteObject(String id) {
	client.delete(id);
    }

    public void urnResolving(String id) {
	client.addUrn(id, "test", "hbz:929:02");

    }

    public void oaiProviding(String id) {
	client.makeOaiSet(namespace + ":" + id);
    }

    public void objecttest(String[] ids) {
	try {
	    for (String id : ids) {
		createObject(id);
		updateObject(id);
		urnResolving(id);
		oaiProviding(id);
	    }
	} catch (Exception e) {
	    logger.info("", e);
	}

	deleteObject(ids[0]);

    }

}
