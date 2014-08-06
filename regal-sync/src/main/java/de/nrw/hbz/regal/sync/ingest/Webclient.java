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
import java.util.List;

import javax.ws.rs.core.MediaType;

import models.DublinCoreData;
import models.ObjectType;
import models.RegalObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;
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
     *            the host of the api. it is assumed that the regal-api is
     *            available under host:8080/api
     */
    public Webclient(String namespace, String user, String password, String host) {
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
	webclient = Client.create(cc);
	webclient.addFilter(new HTTPBasicAuthFilter(user, password));
	endpoint = host;
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
	dc.setIdentifier(dtlBean.getIdentifier());
	String pid = namespace + ":" + dtlBean.getPid();
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
	    updateMetadata(resource + "/metadata", merge);
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
	    updateMetadata(resource + "/metadata", metadata);
	} catch (Exception e) {
	    logger.error(pid + " " + e.getMessage(), e);
	}

    }

    private String appendMetadata(String m, String metadata) {
	return m + "\n" + metadata;
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
	String data = resource + "/data";
	createResource(type, dtlBean);
	updateData(data, dtlBean);
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
	String resourceUrl = endpoint + "/resource/" + pid;
	WebResource resource = webclient.resource(resourceUrl);
	RegalObject input = new RegalObject();
	List<String> ts = dtlBean.getTransformer();
	if (ts != null && !ts.isEmpty())
	    input.setTransformer(ts);
	input.setType(type.toString());
	input.setAccessScheme("private");
	logger.info(pid + " type: " + input.getType());
	if (ppid != null && !ppid.isEmpty()) {
	    input.setParentPid(parentPid);
	}
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

    private void updateMetadata(String url, String metadata) {
	WebResource metadataRes = webclient.resource(url);
	metadataRes.put(metadata);
    }

    private void updateDc(String url, DublinCoreData dc) {
	try {
	    WebResource resource = webclient.resource(url);
	    logger.info("curl -XPUT -uedoweb-admin:admin " + resource + " -d'"
		    + dc.toString() + "'");
	    resource.type("application/json").put(dc.toString());
	} catch (Exception e) {
	    logger.info("", e);
	}
    }

    private void updateLabel(String url, DigitalEntity dtlBean) {
	String pid = namespace + ":" + dtlBean.getPid();
	DublinCoreData dc = new DublinCoreData();
	dc.addTitle("Version of: " + pid);
	dc.addDescription(dtlBean.getLabel());
	updateDc(url + "/dc", dc);
    }

    private void updateData(String url, DigitalEntity dtlBean) {
	String pid = namespace + ":" + dtlBean.getPid();
	WebResource resource = webclient.resource(url);
	Stream dataStream = dtlBean.getStream(StreamType.DATA);
	try {
	    logger.info(pid + " Update data: " + dataStream.getMimeType() + " "
		    + dataStream.getFile().getAbsolutePath());
	    File uploadFile = dataStream.getFile();
	    FormDataMultiPart form = new FormDataMultiPart();
	    FileDataBodyPart body = new FileDataBodyPart("data", uploadFile);
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
	WebResource lobid = webclient.resource(endpoint + "/utils/lobidify/"
		+ namespace + ":" + dtlBean.getPid());
	try

	{
	    lobid.type("text/plain").post();
	} catch (UniformInterfaceException e) {
	    logger.warn(pid + " fetching lobid-data failed", e);
	}
    }

    /**
     * 
     * @param p
     *            A pid to delete
     */
    public void delete(String p) {
	String pid = namespace + ":" + p;

	WebResource delete = webclient.resource(endpoint + "/resource/" + pid);
	try {
	    delete.delete();
	} catch (UniformInterfaceException e) {
	    logger.info(pid + " Can't delete!" + e.getMessage(), e);
	}
    }

    /**
     * @param dtlBean
     *            a dtlBean with metadata
     */
    public void makeOaiSet(DigitalEntity dtlBean) {

	String pid = namespace + ":" + dtlBean.getPid();
	WebResource oaiSet = webclient.resource(endpoint + "/resource/"
		+ namespace + ":" + dtlBean.getPid() + "/oaisets");
	try {
	    oaiSet.post();
	} catch (UniformInterfaceException e) {
	    logger.warn(pid + " " + "Not oai provided! " + e.getMessage(), e);
	}

    }

    /**
     * init all known contentModels/Transformers
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
	WebResource resource = webclient.resource(endpoint
		+ "/utils/addUrn?id=" + id + "&namespace=" + ns + "&snid="
		+ snid);
	resource.post();
    }
}
