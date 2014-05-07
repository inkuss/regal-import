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
package de.nrw.hbz.regal.search;

import java.util.List;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.search.SearchHits;

/**
 * @author Jan Schnasse schnasse@hbz-nrw.de
 * 
 */
public class SearchFacade {
    Search search;

    /**
     * @param cluster
     *            the name must match to the one provided in
     *            elasticsearch/conf/elasticsearch.yml
     * @param config
     *            elasticsearch mapping
     */
    public SearchFacade(String cluster, String config) {
	InetSocketTransportAddress server = new InetSocketTransportAddress(
		"localhost", 9300);
	Client client = new TransportClient(ImmutableSettings.settingsBuilder()
		.put("cluster.name", cluster).build())
		.addTransportAddress(server);
	search = new Search(client);
	init("edoweb", config);
    }

    /**
     * @param index
     *            the index will be inititiated
     * @param config
     *            an elasticsearch mapping
     */
    public void init(String index, String config) {
	search.init(index, config);
    }

    /**
     * @param index
     *            name of the elasticsearch index. will be created, if not
     *            exists.
     * @param type
     *            the type of the indexed item
     * @param id
     *            the id of the indexed item
     * @param data
     *            the actual item
     * @return the Response
     */
    public ActionResponse index(String index, String type, String id,
	    String data) {
	return search.index(index, type, id, data);
    }

    /**
     * @param index
     *            name of the elasticsearch index. will be created, if not
     *            exists.
     * @param type
     *            the type of the indexed item
     * @param from
     *            use from and until to page through the results
     * @param until
     *            use from and until to page through the results
     * @return hits for the search
     */
    public SearchHits listResources(String index, String type, int from,
	    int until) {
	return search.listResources(index, type, from, until);
    }

    /**
     * Gives a list of id's
     * 
     * @param index
     *            name of the elasticsearch index. will be created, if not
     *            exists.
     * @param type
     *            the type of the indexed item
     * @param from
     *            use from and until to page through the results
     * @param until
     *            use from and until to page through the results
     * @return a list of ids
     */
    public List<String> listIds(String index, String type, int from, int until) {
	return search.listIds(index, type, from, until);
    }

    /**
     * Deletes a certain item
     * 
     * @param index
     *            name of the elasticsearch index. will be created, if not
     *            exists.
     * @param type
     *            the type of the indexed item
     * @param id
     *            the item's id
     * @return the response
     */
    public ActionResponse delete(String index, String type, String id) {
	return search.delete(index, type, id);
    }

    /**
     * @param index
     *            the elastic search index
     * @param fieldName
     *            the field to search in
     * @param fieldValue
     *            the value to search for
     * @return all hits
     */
    public SearchHits query(String index, String fieldName, String fieldValue) {
	return search.query(index, fieldName, fieldValue);
    }

    /**
     * @param index
     *            the index you want the settings for
     * @param type
     *            the type
     * @return settings for a particular type and index
     */
    public String getSettings(String index, String type) {
	return search.getSettings(index, type);
    }

    public String toString() {
	return search.toString();
    }

}
