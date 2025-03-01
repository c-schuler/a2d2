// Copyright 2018-2020 Elimu Informatics
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.elimu.a2d2.cds.fhir.helper.r4;

import java.util.LinkedList;
import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import io.elimu.a2d2.cds.fhir.helper.FhirClientWrapper;
import io.elimu.a2d2.cds.fhir.helper.FhirResponse;
import io.elimu.a2d2.cds.fhir.helper.QueryingCallback;
import io.elimu.a2d2.cds.fhir.helper.QueryingServerHelperBase;

/**
 * QueryingServerHelper implementation for FHIR R4 servers and model.
 * Please also check methods at {@link QueryingServerHelperBase} because this class will contain all 
 * the methods available there as well.
 */
public class QueryingServerHelper extends QueryingServerHelperBase<QueryingServerHelper, Bundle> {

	/**
	 * Default Constructor to use
	 * @param url it will be the baseUrl for the FHIR server. Basically, if you do a query for all Patient objects in your FHIR R4 server, then you remove the "/Patient" from the URL, you have what should be placed here
	 */
	public QueryingServerHelper(String url) {
		this(url, FhirVersion.FHIR4);
	}
	
	public QueryingServerHelper() {
		this(null, FhirVersion.FHIR4);
	}
	
	public QueryingServerHelper(String url, FhirVersion fhirVersion) {
		super(url, fhirVersion, FhirVersionEnum.R4);
	}

	/**
	 * @deprecated (in favor of queryResourcesResponse)
	 */
	@Deprecated
	public List<IBaseResource> queryResources(String resourceType, String subjectId, String subjectRefAttribute,
			String fhirQuery) {
		return queryResourcesResponse(resourceType, subjectId, subjectRefAttribute, fhirQuery).getResult();
	}
	
	/**
	 * @deprecated (in favor of getResourceByIdResponse)
	 */
	@Deprecated
	public IBaseResource getResourceById(String resourceType, String resourceId) {
		return getResourceByIdResponse(resourceType, resourceId).getResult();
	}

	@Override 
	public FhirResponse<IBaseResource> fetchServer(final String resourceType, String resourceQuery) {
		FhirClientWrapper client = clients.next();
		FhirResponse<IBaseResource> resource = null;
		log.debug("Fetching {} {} resource using client for version {} ", client.getFhirContext().getVersion().getVersion().name(),
				resourceType,  client.getFhirContext().getVersion().getVersion().name());
		resource = runWithInterceptors(new QueryingCallback<IBaseResource>() {
			@Override
			@SuppressWarnings("unchecked")
			public IBaseResource execute(FhirClientWrapper client) {
				log.debug("Invoking url {}", resourceQuery);
				try {
					ClassLoader cl = Thread.currentThread().getContextClassLoader();
					Class<IBaseResource> clz = (Class<IBaseResource>) cl.loadClass("org.hl7.fhir.r4.model." + resourceType);
					return client.fetchResourceFromUrl(clz, resourceQuery);
				} catch (ClassNotFoundException e) {
					log.error("ResourceType " + resourceType + " not supported for direct path reading in fhir " + client.getFhirContext().getVersion().getVersion().name());
					return null;
				}
			}
		}, client);
		if (resource == null) {
			return new FhirResponse<>(null, 404, "Not Found");
		}
		return resource;
	}
	
	@Override
	public FhirResponse<List<IBaseResource>> queryServer(String resourceQuery) {
		FhirClientWrapper client = clients.next();
		FhirResponse<List<IBaseResource>> resourceBundle = null;
		log.debug("Fetching {} resources using client for version {} ", client.getFhirContext().getVersion().getVersion().name(),
				client.getFhirContext().getVersion().getVersion().name());
		resourceBundle = runWithInterceptors(new QueryingCallback<List<IBaseResource>>() {
			@Override
			public List<IBaseResource> execute(FhirClientWrapper client) {
				log.debug("Invoking url {}", resourceQuery);
				Bundle bundle = client.fetchResourceFromUrl(Bundle.class, resourceQuery);
				List<IBaseResource> retval = new LinkedList<>();
				bundle.getEntry().forEach(e -> retval.add((IBaseResource) e.getResource()));
				while (bundle.getLink("next") != null) {
					final String url = bundle.getLink("next").getUrl();
					log.debug("Invoking url {}", url);
					bundle = client.fetchResourceFromUrl(Bundle.class, url);
					bundle.getEntry().forEach(e -> retval.add((IBaseResource) e.getResource()));
				}
				return retval;
			}
		}, client);
		if (resourceBundle.getResult() == null) {
			return new FhirResponse<>(null, resourceBundle.getResponseStatusCode(), resourceBundle.getResponseStatusInfo());
		}
		return resourceBundle;
	}
	
	public static enum FhirVersion implements FhirVersionAbs {
		
		FHIR4(FhirContext.forR4());
		
		private final FhirContext ctx;

		FhirVersion(FhirContext ctx) {
			this.ctx = ctx;
			// initialize the factory
			IRestfulClientFactory factory = ctx.getRestfulClientFactory();
			iRestfulClientFactoryConfig(factory);
		}

		public FhirContext getCtx() {
			return ctx;
		}
	}

}
