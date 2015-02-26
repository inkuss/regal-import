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
package de.nrw.hbz.regal.sync.ingest;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.ws.rs.core.MediaType;

import models.DublinCoreData;
import models.ObjectType;
import models.RegalObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.AsyncWebResource;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.file.FileDataBodyPart;
import com.sun.jersey.multipart.impl.MultiPartWriter;

import de.nrw.hbz.regal.sync.extern.DigitalEntity;
import de.nrw.hbz.regal.sync.extern.Stream;
import de.nrw.hbz.regal.sync.extern.StreamType;

/**
 * Webclient collects typical api-calls and make them available in the
 * regal-sync module
 * 
 * @author Jan Schnasse schnasse@hbz-nrw.de
 * 
 */
public class Webclient {

    final static Logger logger = LoggerFactory.getLogger(Webclient.class);
    String namespace = null;
    String endpoint = null;
    String host = null;
    Client webclient = null;

    /**
     * @param namespace
     *            The namespace is used to prefix pids for resources
     * @param user
     *            a valid user to authenticate to the webapi
     * @param password
     *            a password for the webapi
     * @param host
     *            the host of the api.
     * @param kconf
     *            config for keystore, if null the client will go against
     *            unsecured http
     */
    public Webclient(String namespace, String user, String password,
	    String host, KeystoreConf kconf) {
	this.host = host;
	this.namespace = namespace;
	ClientConfig cc = new DefaultClientConfig();
	cc.getClasses().add(MultiPartWriter.class);
	cc.getClasses().add(FormDataMultiPart.class);
	cc.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, true);
	cc.getFeatures().put(ClientConfig.FEATURE_DISABLE_XML_SECURITY, true);
	cc.getProperties().put(
		DefaultApacheHttpClientConfig.PROPERTY_CHUNKED_ENCODING_SIZE,
		1024);
	if (kconf != null && kconf.location != null && kconf.password != null) {
	    cc.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
		    new HTTPSProperties(null, initSsl(cc, kconf)));
	    endpoint = "https://" + host;

	    System.out.println("Connect via https");
	} else {
	    System.out.println("Connect via http");
	    endpoint = "http://" + host;
	}
	webclient = Client.create(cc);
	webclient.addFilter(new HTTPBasicAuthFilter(user, password));
    }

    private SSLContext initSsl(ClientConfig cc, KeystoreConf kconf) {
	try {
	    SSLContext ctx = SSLContext.getInstance("SSL");

	    KeyStore trustStore;
	    trustStore = KeyStore.getInstance("JKS");
	    trustStore.load(new FileInputStream(kconf.location),
		    kconf.password.toCharArray());
	    TrustManagerFactory tmf = TrustManagerFactory
		    .getInstance("SunX509");
	    tmf.init(trustStore);

	    ctx.init(null, tmf.getTrustManagers(), null);
	    return ctx;
	} catch (Exception e) {
	    throw new RuntimeException("Can not initiate SSL connection", e);
	}
    }

    /**
     * Metadata performs typical metadata related api-actions like update the dc
     * stream enrich with catalogdata. Add the object to the searchindex and
     * provide it on the oai interface.
     * 
     * @param dtlBean
     *            A DigitalEntity to operate on
     */
    public void autoGenerateMetdata(DigitalEntity dtlBean) {
	try {
	    setIdentifier(dtlBean);
	    lobidify(dtlBean);
	} catch (Exception e) {
	    logger.error(dtlBean.getPid() + " " + e.getMessage(), e);
	}
    }

    private void setIdentifier(DigitalEntity dtlBean) {
	DublinCoreData dc = new DublinCoreData();
	for (String id : dtlBean.getIdentifier()) {
	    dc.addIdentifier(id, "");
	}
	addIdentifier(dc, dtlBean.getPid(), namespace);
    }

    /**
     * @param dc
     *            dublin core data
     * @param id
     *            id of the object withoug namespace
     * @param namespace
     *            the namespace
     */
    public void addIdentifier(DublinCoreData dc, String id, String namespace) {
	String pid = namespace + ":" + id;
	String resource = endpoint + "/resource/" + pid + "/dc";
	updateDc(resource, dc);
    }

    /**
     * Metadata performs typical metadata related api-actions like update the dc
     * stream enrich with catalogdata. Add the object to the searchindex and
     * provide it on the oai interface.
     * 
     * @param dtlBean
     *            A DigitalEntity to operate on
     * @param metadata
     *            n-triple metadata to integrate
     */
    public void autoGenerateMetadataMerge(DigitalEntity dtlBean, String metadata) {
	setMetadata(dtlBean, "");
	autoGenerateMetdata(dtlBean);
	String pid = namespace + ":" + dtlBean.getPid();
	String resource = endpoint + "/resource/" + pid;
	String m = "";
	try {
	    logger.debug("Metadata: " + metadata);
	    m = readMetadata(resource + "/metadata", dtlBean);
	} catch (Exception e) {
	    logger.error(dtlBean.getPid() + " " + e.getMessage(), e);
	}
	try {
	    String merge = appendMetadata(m, metadata);
	    logger.debug("MERGE: " + metadata);
	    updateMetadata(resource + "/metadata", merge, "text/plain");
	} catch (Exception e) {
	    logger.error(dtlBean.getPid() + " " + e.getMessage(), e);
	}
    }

    /**
     * Sets the metadata to a resource represented by the passed DigitalEntity.
     * 
     * @param dtlBean
     *            The bean for the object
     * @param metadata
     *            The metadata
     */
    public void setMetadata(DigitalEntity dtlBean, String metadata) {
	String pid = namespace + ":" + dtlBean.getPid();
	String resource = endpoint + "/resource/" + pid;
	try {
	    updateMetadata(resource + "/metadata", metadata, "text/plain");
	} catch (Exception e) {
	    logger.error(pid + " " + e.getMessage(), e);
	}
    }

    private String appendMetadata(String m, String metadata) {
	return m + "\n" + metadata;
    }

    /**
     * @param dtlBean
     *            the digitool entity
     * @param parts
     *            an orderered list of parts
     */
    public void createSeq(DigitalEntity dtlBean, List<String> parts) {
	String pid = namespace + ":" + dtlBean.getPid();
	String resource = endpoint + "/resource/" + pid;
	try {
	    StringBuffer json = new StringBuffer();
	    json.append("[");
	    for (String p : parts) {
		json.append("\"" + namespace + ":" + p + "\",");
	    }
	    json.delete(json.length() - 1, json.length());
	    json.append("]");
	    logger.debug(json.toString());
	    updateMetadata(resource + "/parts", json.toString(),
		    "application/json");
	} catch (Exception e) {
	    logger.error(pid + " " + e.getMessage(), e);
	}
    }

    /**
     * @param dtlBean
     *            A DigitalEntity to operate on.
     * @param type
     *            The Object type
     */
    public void createObject(DigitalEntity dtlBean, ObjectType type) {
	String pid = namespace + ":" + dtlBean.getPid();
	String resource = endpoint + "/resource/" + pid;
	createResource(type, dtlBean);
	updateData(dtlBean);
	updateLabel(resource, dtlBean);
    }

    /**
     * @param type
     *            The ObjectType .
     * @param dtlBean
     *            The DigitalEntity to operate on
     */
    public void createResource(ObjectType type, DigitalEntity dtlBean) {
	String pid = namespace + ":" + dtlBean.getPid();
	String ppid = dtlBean.getParentPid();
	logger.info(pid + " is child of " + dtlBean.getParentPid());
	String parentPid = namespace + ":" + ppid;
	RegalObject input = new RegalObject();
	List<String> ts = dtlBean.getTransformer();
	if (ts != null && !ts.isEmpty())
	    input.setTransformer(ts);
	input.setContentType(type.toString());
	input.setAccessScheme("public");
	input.setPublishScheme("public");
	input.getIsDescribedBy().setCreatedBy(dtlBean.getCreatedBy());
	input.getIsDescribedBy().setImportedFrom(dtlBean.getImportedFrom());
	input.getIsDescribedBy().setLegacyId(dtlBean.getLegacId());
	logger.info(pid + " type: " + input.getContentType());
	if (ppid != null && !ppid.isEmpty()) {
	    input.setParentPid(parentPid);
	}
	createResource(input, pid);
    }

    /**
     * @param input
     *            RegalObject
     * @param pid
     *            the namespace qualified pid
     */
    public void createResource(RegalObject input, String pid) {
	String resourceUrl = endpoint + "/resource/" + pid;
	WebResource resource = webclient.resource(resourceUrl);
	try {
	    logger.info("curl -XPUT -uedoweb-admin:admin -d'" + input + "' "
		    + resource);
	    resource.type("application/json").put(input.toString());
	} catch (Exception e) {
	    logger.info(pid + " " + e.getMessage(), e);
	}
    }

    private String readMetadata(String url, DigitalEntity dtlBean) {
	WebResource metadataRes = webclient.resource(url);
	return metadataRes.get(String.class);
    }

    private void updateMetadata(String url, String metadata,
	    String contentType, String charset) {
	WebResource metadataRes = webclient.resource(url);
	logger.debug(url);
	metadataRes.type(contentType + ";charset=" + charset).put(metadata);
    }

    private void updateMetadata(String url, String metadata, String contentType) {
	updateMetadata(url, metadata, contentType, "utf-8");
    }

    private void updateDc(String url, DublinCoreData dc) {
	String response = "";
	try {
	    WebResource resource = webclient.resource(url);
	    logger.info("curl -XPUT -uedoweb-admin:admin " + resource + " -d'"
		    + dc.toString() + "'");
	    response = resource.type("application/json").put(String.class,
		    dc.toString());
	} catch (Exception e) {
	    logger.info(response, e);
	}
    }

    private void updateLabel(String url, DigitalEntity dtlBean) {
	String pid = namespace + ":" + dtlBean.getPid();
	DublinCoreData dc = new DublinCoreData();
	dc.addTitle("Version of: " + pid);
	dc.addDescription(dtlBean.getLabel());
	updateDc(url + "/dc", dc);
    }

    private void updateData(DigitalEntity dtlBean) {
	String pid = namespace + ":" + dtlBean.getPid();
	Stream dataStream = dtlBean.getStream(StreamType.DATA);
	File data = dataStream.getFile();
	String mimeType = dataStream.getMimeType();
	try {
	    updateData(pid, data, mimeType);
	} catch (UniformInterfaceException e) {
	    logger.error(pid + " " + e.getMessage(), e);
	} catch (Exception e) {
	    logger.error(pid + " " + e.getMessage(), e);
	}
    }

    /**
     * @param pid
     *            the namespace qualified pid
     * @param data
     *            data to upload
     * @param mimeType
     *            mimetype of data
     */
    public void updateData(String pid, File data, String mimeType) {
	try {
	    WebResource resource = webclient.resource(endpoint + "/resource/"
		    + pid + "/data");
	    logger.info(pid + " Update data: " + mimeType + " "
		    + data.getAbsolutePath());
	    FormDataMultiPart form = new FormDataMultiPart();
	    FileDataBodyPart body = new FileDataBodyPart("data", data);
	    form.bodyPart(body);
	    resource.type(MediaType.MULTIPART_FORM_DATA_TYPE).put(form);
	} catch (UniformInterfaceException e) {
	    logger.error(pid + " " + e.getMessage(), e);
	} catch (Exception e) {
	    logger.error(pid + " " + e.getMessage(), e);
	}
    }

    private void lobidify(DigitalEntity dtlBean) {
	String pid = namespace + ":" + dtlBean.getPid();
	try {
	    lobidify(pid);
	} catch (UniformInterfaceException e) {
	    logger.warn(pid + " fetching lobid-data failed", e);
	}
    }

    /**
     * Calls the lobidify RPC of regal-api to get metadata for the object
     * 
     * @param pid
     *            a namespace qualified pid
     */
    public void lobidify(String pid) {
	WebResource lobid = webclient.resource(endpoint + "/utils/lobidify/"
		+ pid);
	lobid.type("text/plain").post();
    }

    /**
     * 
     * @param id
     *            A id without namespace to delete
     */
    public void deleteId(String id) {
	String pid = namespace + ":" + id;
	try {
	    delete(pid);
	} catch (Exception e) {
	    logger.info(pid + " Can't delete!" + e.getMessage(), e);
	}
    }

    /**
     * @param pid
     *            a pid with namespace
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public void delete(String pid) throws InterruptedException,
	    ExecutionException {
	AsyncWebResource delete = webclient.asyncResource(endpoint
		+ "/resource/" + pid);
	String response = delete.delete(String.class).get();
	System.out.println(response.toString());
    }

    /**
     * @param dtlBean
     *            a dtlBean with metadata
     */
    public void makeOaiSet(DigitalEntity dtlBean) {

	String pid = namespace + ":" + dtlBean.getPid();
	try {
	    makeOaiSet(pid);
	} catch (UniformInterfaceException e) {
	    logger.warn(pid + " " + "Not oai provided! " + e.getMessage(), e);
	}
    }

    /**
     * Creates oai sets for the object
     * 
     * @param pid
     *            a namespace qualified pid
     */
    public void makeOaiSet(String pid) {
	WebResource oaiSet = webclient.resource(endpoint + "/resource/" + pid
		+ "/oaisets");
	oaiSet.post();
    }

    /**
     * init all known contentModels/Transformers
     * 
     * @param namespace
     *            only content models for specific namespace will being
     *            initialised
     */
    public void initContentModels(String namespace) {
	WebResource resource = webclient.resource(endpoint
		+ "/utils/initContentModels?namespace=" + namespace);
	resource.post();
    }

    /**
     * @param id
     *            pid without namespace
     * @param ns
     *            namespace of the pid
     * @param snid
     *            urn subnamespace id
     */
    public void addUrn(String id, String ns, String snid) {
	try {
	    WebResource resource = webclient.resource(endpoint
		    + "/utils/addUrn?id=" + id + "&namespace=" + ns + "&snid="
		    + snid);
	    resource.post();
	} catch (Exception e) {
	    logger.info(id + " already has urn.");
	}
    }

    /**
     * @param pid
     *            a namespace qualified pid
     * @return json representation of resource
     */
    public String readResource(String pid) {
	String resourceUrl = endpoint + "/resource/" + pid;
	WebResource resource = webclient.resource(resourceUrl);
	try {
	    logger.info("curl -XGET -uedoweb-admin:admin " + resource);
	    String response = resource.type("application/json")
		    .accept("application/json").get(String.class);
	    return response;
	} catch (Exception e) {
	    throw new RuntimeException("", e);
	}
    }

    /**
     * @param pid
     *            a namespace qualified pid
     * @return json representation of resource
     */
    public String readResourceIndex(String pid) {
	String resourceUrl = endpoint + "/resourceIndex/" + pid;
	WebResource resource = webclient.resource(resourceUrl);
	try {
	    logger.info("curl -XGET -uedoweb-admin:admin " + resource);
	    String response = resource.type("application/json")
		    .accept("application/json").get(String.class);
	    return response;
	} catch (Exception e) {
	    throw new RuntimeException("", e);
	}
    }

    public String moveUp(String pid) {
	String resourceUrl = endpoint + "/resource/" + pid + "/moveUp";
	WebResource resource = webclient.resource(resourceUrl);
	try {
	    logger.info("curl -XPOST -uedoweb-admin:admin " + resource);
	    String response = resource.accept("application/json").post(
		    String.class);
	    return response;
	} catch (Exception e) {
	    throw new RuntimeException("", e);
	}
    }

    public String getMetadataFromParent(String pid) {
	String resourceUrl = endpoint + "/resource/" + pid + "/metadata/copy";
	WebResource resource = webclient.resource(resourceUrl);
	try {
	    logger.info("curl -XPOST -uedoweb-admin:admin " + resource);
	    String response = resource.accept("application/json").post(
		    String.class);
	    return response;
	} catch (Exception e) {
	    throw new RuntimeException("", e);
	}
    }

    public String getMetadata(String pid) {
	String resourceUrl = endpoint + "/resource/" + pid + "/metadata";
	WebResource resource = webclient.resource(resourceUrl);
	try {
	    logger.info("curl -XGET -uedoweb-admin:admin " + resource);
	    String response = resource.accept("text/plain").get(String.class);
	    return response;
	} catch (Exception e) {
	    throw new RuntimeException("", e);
	}
    }

    public String setMetadata(String pid, String metadata) {
	String resourceUrl = endpoint + "/resource/" + pid + "/metadata";
	WebResource resource = webclient.resource(resourceUrl);
	try {
	    logger.info("curl -XPUT -uedoweb-admin:admin " + resource);
	    resource.accept("text/plain").type("text/plain").put(metadata);
	    return resource.get(String.class);
	} catch (Exception e) {
	    throw new RuntimeException("", e);
	}
    }

    public String flatten(String pid) {
	String resourceUrl = endpoint + "/resource/" + pid + "/flatten";
	WebResource resource = webclient.resource(resourceUrl);
	try {
	    logger.info("curl -XPOST -uedoweb-admin:admin " + resource);
	    String response = resource.accept("text/plain").type("text/plain")
		    .post(String.class);
	    return response;
	} catch (Exception e) {
	    throw new RuntimeException("", e);
	}
    }

    public String flattenAll(String pid) {
	String resourceUrl = endpoint + "/resource/" + pid + "/all/flatten";
	WebResource resource = webclient.resource(resourceUrl);
	try {
	    logger.info("curl -XPOST -uedoweb-admin:admin " + resource);
	    String response = resource.accept("text/plain").type("text/plain")
		    .post(String.class);
	    return response;
	} catch (Exception e) {
	    throw new RuntimeException("", e);
	}
    }

}
