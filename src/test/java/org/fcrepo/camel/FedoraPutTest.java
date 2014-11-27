/**
 * Copyright 2014 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.camel;

import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.fcrepo.camel.FedoraEndpoint.FCREPO_IDENTIFIER;
import static org.fcrepo.camel.integration.FedoraTestUtils.getFcrepoEndpointUri;
import static org.fcrepo.camel.integration.FedoraTestUtils.getTurtleDocument;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test adding an RDF resource with PUT
 * @author Aaron Coburn
 * @since November 7, 2014
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"/spring-test/test-container.xml"})
public class FedoraPutTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Test
    public void testPut() throws InterruptedException {
        final String path1 = "/test/a/b/c/d";
        final String path2 = "/test/a/b/c/e";

        // Assertions
        resultEndpoint.expectedMessageCount(2);

        // Setup
        final Map<String, Object> setupHeaders = new HashMap<>();
        setupHeaders.put(HTTP_METHOD, "PUT");
        setupHeaders.put(FCREPO_IDENTIFIER, path1);
        setupHeaders.put(CONTENT_TYPE, "text/turtle");
        template.sendBodyAndHeaders("direct:setup", getTurtleDocument(), setupHeaders);

        setupHeaders.clear();
        setupHeaders.put(HTTP_METHOD, "PUT");
        setupHeaders.put(FCREPO_IDENTIFIER, path2);
        template.sendBodyAndHeaders("direct:setup2", getTurtleDocument(), setupHeaders);

        // Test
        final Map<String, Object> headers = new HashMap<>();
        headers.put(FCREPO_IDENTIFIER, path1);

        template.sendBodyAndHeaders(null, headers);

        headers.clear();
        headers.put(CONTENT_TYPE, "text/plain");
        headers.put(FCREPO_IDENTIFIER, path2);
        template.sendBodyAndHeaders("foo", headers);

        // Teardown
        final Map<String, Object> teardownHeaders = new HashMap<>();
        teardownHeaders.put(HTTP_METHOD, "DELETE");
        teardownHeaders.put(FCREPO_IDENTIFIER, path1);
        template.sendBodyAndHeaders("direct:teardown", null, teardownHeaders);

        teardownHeaders.clear();
        teardownHeaders.put(HTTP_METHOD, "DELETE");
        teardownHeaders.put(FCREPO_IDENTIFIER, path2);
        template.sendBodyAndHeaders("direct:teardown", null, teardownHeaders);

        // Confirm that assertions passed
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                final String fcrepo_uri = getFcrepoEndpointUri();

                final Namespaces ns = new Namespaces("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");

                from("direct:setup")
                    .to(fcrepo_uri);

                from("direct:setup2")
                    .to(fcrepo_uri + "?contentType=text/turtle");

                from("direct:start")
                    .to(fcrepo_uri)
                    .log("${headers}")
                    .filter().xpath(
                        "/rdf:RDF/rdf:Description/rdf:type" +
                        "[@rdf:resource='http://fedora.info/definitions/v4/repository#Resource']", ns)
                    .to("mock:result");

                from("direct:teardown")
                    .to(fcrepo_uri)
                    .to(fcrepo_uri + "?tombstone=true");
            }
        };
    }
}