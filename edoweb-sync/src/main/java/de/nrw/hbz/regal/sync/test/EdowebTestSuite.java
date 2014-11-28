package de.nrw.hbz.regal.sync.test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
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

    private RegalObject readToRegalObject(InputStream in) throws Exception {
	ObjectMapper mapper = new ObjectMapper();
	mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
		false);
	RegalObject o = mapper.readValue(in, RegalObject.class);
	return o;
    }

    private Map readToMap(InputStream in) throws Exception {
	ObjectMapper mapper = new ObjectMapper();
	mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
		false);
	Map<String, Object> o = mapper.readValue(in, Map.class);
	return o;
    }

    private RegalObject read(String id) {
	try {
	    return readToRegalObject(getClassLoader().getResourceAsStream(
		    "examples/" + id + "/create.json"));
	} catch (Exception e) {
	    throw new RuntimeException("Can`t read in " + id, e);
	}
    }

    public void createObject(String id) {
	try {
	    String pid = namespace + ":" + id;
	    RegalObject input = read(id);
	    client.createResource(input, pid);
	    RegalObject output = readToRegalObject(str2stream(client
		    .readResource(pid)));
	    Assert.assertEquals(input, output);
	    Assert.assertEquals(true, readTest(pid));
	    findData(id);
	} catch (Exception e) {
	    System.out.println("Can`t read in " + id);
	    e.printStackTrace();
	    Assert.fail();
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
	try {
	    client.lobidify("test:" + id);
	} catch (Exception e) {
	    System.out.println("lobidify is optional - failed for " + id);
	}
    }

    public void deleteObject(String id) {
	client.deleteId(id);
    }

    public void urnResolving(String id) throws Exception {
	try {
	    logger.error("URN " + id);
	    client.addUrn(id, "test", "hbz:929:02");
	} catch (Exception e) {
	    Map<String, Object> out = readToMap(str2stream(client
		    .readResource(namespace + ":" + id)));
	    logger.info(out.toString(), e);
	}
    }

    public void oaiProviding(String id) {
	client.makeOaiSet(namespace + ":" + id);
    }

    private void testHasParent(String parentId) throws Exception {
	String pid = "test:1234567";
	String parentPid = namespace + ":" + parentId;
	RegalObject input = new RegalObject();
	input.setContentType("file");
	input.setParentPid(parentPid);
	client.createResource(input, pid);
	Map<String, Object> child = readToMap(str2stream(client
		.readResource(pid)));
	Map<String, Object> parent = readToMap(str2stream(client
		.readResource(parentPid)));
	String cpp = (String) child.get("parentPid");
	Assert.assertEquals(parentPid, cpp);
	List<String> pcp = (List<String>) parent.get("hasPart");
	int size = pcp.size();
	Assert.assertTrue(size >= 1);
	Assert.assertTrue(pcp.contains(pid));
	client.delete(pid);
	Assert.assertEquals(false, readTest(pid));
	parent = readToMap(str2stream(client.readResource(parentPid)));
	if (parent.containsKey("hasPart")) {
	    List<String> parts = (List<String>) parent.get("hasPart");
	    Assert.assertFalse(parts.contains(pid));
	    Assert.assertTrue(parts.size() == size - 1);
	}
    }

    public boolean readTest(String pid) {
	boolean success = false;
	try {
	    client.readResourceIndex(pid);
	    success = true;
	} catch (Exception e) {
	    System.out.println(pid + " not found in elasticsearch.");
	}
	try {
	    client.readResource(pid);
	    success = true;
	} catch (Exception e) {
	    System.out.println(pid + " not found in fedora.");
	}
	return success;

    }

    public void objecttest(String[] ids) {
	for (String id : ids) {
	    try {
		logger.info("Test: " + id);
		createObject(id);
		testHasParent(id);
		updateObject(id);
		urnResolving(id);
		oaiProviding(id);
	    } catch (Exception e) {
		logger.info("", e);
	    }
	}
	deleteObject(ids[0]);
	for (String id : ids) {
	    System.out.println("Delete " + id);
	    Assert.assertEquals(false, readTest(namespace + ":" + id));
	}
    }

}