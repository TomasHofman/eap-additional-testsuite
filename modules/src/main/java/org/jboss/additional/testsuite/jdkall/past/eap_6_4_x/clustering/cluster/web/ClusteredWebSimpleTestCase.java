/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.additional.testsuite.jdkall.past.eap_6_4_x.clustering.cluster.web;

import static org.jboss.additional.testsuite.jdkall.past.eap_6_4_x.clustering.ClusterTestUtil.waitForReplication;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.additional.testsuite.jdkall.past.eap_6_4_x.clustering.cluster.ClusterAbstractTestCase;
import org.jboss.additional.testsuite.jdkall.past.eap_6_4_x.clustering.single.web.SimpleSecuredServlet;
import org.jboss.additional.testsuite.jdkall.past.eap_6_4_x.clustering.single.web.SimpleServlet;
import org.jboss.as.test.http.util.HttpClientUtils;
import org.jboss.as.test.integration.web.sso.SSOTestBase;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jboss.eap.additional.testsuite.annotations.EapAdditionalTestsuite;

/**
 * Validate the <distributable/> works for a two-node cluster.
 *
 * @author Paul Ferraro
 * @author Radoslav Husar
 */
@RunWith(Arquillian.class)
@RunAsClient
@EapAdditionalTestsuite({"modules/testcases/jdkAll/Eap64x/clustering/src/main/java","modules/testcases/jdkAll/Eap64x-Proposed/clustering/src/main/java","modules/testcases/jdkAll/Eap63x/clustering/src/main/java","modules/testcases/jdkAll/Eap62x/clustering/src/main/java"})
public class ClusteredWebSimpleTestCase extends ClusterAbstractTestCase {

    private static final int REQUEST_DURATION = 10000;

    @Deployment(name = DEPLOYMENT_1, managed = false)
    @TargetsContainer(CONTAINER_1)
    public static Archive<?> deployment0() {
        return getDeployment();
    }

    @Deployment(name = DEPLOYMENT_2, managed = false)
    @TargetsContainer(CONTAINER_2)
    public static Archive<?> deployment1() {
        return getDeployment();
    }

    private static Archive<?> getDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "distributable.war");
        war.addClasses(SimpleServlet.class, SimpleSecuredServlet.class);
        war.setWebXML("web.xml");
        log.info(war.toString(true));
        return war;
    }

    @Override
    protected void setUp() {
        super.setUp();
    }

    @Test
    @InSequence(1)
    public void setupSSO(@ArquillianResource @OperateOnDeployment(DEPLOYMENT_1) ManagementClient client1,
            @ArquillianResource @OperateOnDeployment(DEPLOYMENT_2) ManagementClient client2) throws Exception {

        // add sso valves
        SSOTestBase.addClusteredSso(client1.getControllerClient(), "standalone");
        SSOTestBase.addClusteredSso(client2.getControllerClient(), "standalone");

        stop(CONTAINERS);
        start(CONTAINERS);
        deploy(DEPLOYMENTS);

    }

    @Test
    @InSequence(7)
    public void stopServers(@ArquillianResource @OperateOnDeployment(DEPLOYMENT_1) ManagementClient client1,
            @ArquillianResource @OperateOnDeployment(DEPLOYMENT_2) ManagementClient client2) throws Exception {

        SSOTestBase.removeSso(client1.getControllerClient(), "standalone");
        SSOTestBase.removeSso(client2.getControllerClient(), "standalone");

        undeploy(DEPLOYMENTS);
        stop(CONTAINERS);
    }

    @Test
    @InSequence(2)
    @OperateOnDeployment(DEPLOYMENT_1)
    public void testSerialized(@ArquillianResource(SimpleServlet.class) URL baseURL) throws ClientProtocolException,
            IOException {
        DefaultHttpClient client = HttpClientUtils.relaxedCookieHttpClient();

        // returns the URL of the deployment (http://127.0.0.1:8180/distributable)
        final String url = baseURL.toString() + SimpleServlet.URL;
        log.info("URL = " + url);

        try {
            HttpResponse response = client.execute(new HttpGet(url));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(1, Integer.parseInt(response.getFirstHeader("value").getValue()));
            Assert.assertFalse(Boolean.valueOf(response.getFirstHeader("serialized").getValue()));
            response.getEntity().getContent().close();

            response = client.execute(new HttpGet(url));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(2, Integer.parseInt(response.getFirstHeader("value").getValue()));
            // This won't be true unless we have somewhere to which to replicate
            Assert.assertTrue(Boolean.valueOf(response.getFirstHeader("serialized").getValue()));
            response.getEntity().getContent().close();
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment(DEPLOYMENT_2)
    // For change, operate on the 2nd deployment first
    public void testSessionReplication(
            @ArquillianResource(SimpleServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1,
            @ArquillianResource(SimpleServlet.class) @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2)
            throws IllegalStateException, IOException, InterruptedException {
        DefaultHttpClient client = HttpClientUtils.relaxedCookieHttpClient();

        String url1 = baseURL1.toString() + SimpleServlet.URL;
        String url2 = baseURL2.toString() + SimpleServlet.URL;

        try {
            HttpResponse response = client.execute(new HttpGet(url1));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(1, Integer.parseInt(response.getFirstHeader("value").getValue()));
            response.getEntity().getContent().close();

            // Lets do this twice to have more debug info if failover is slow.
            response = client.execute(new HttpGet(url1));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(2, Integer.parseInt(response.getFirstHeader("value").getValue()));
            response.getEntity().getContent().close();

            // Lets wait for the session to replicate
            waitForReplication(GRACE_TIME_TO_REPLICATE);

            // Now check on the 2nd server

            // Note that this DOES rely on the fact that both servers are running on the "same" domain,
            // which is '127.0.0.0'. Otherwise you will have to spoof cookies. @Rado
            response = client.execute(new HttpGet(url2));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(3, Integer.parseInt(response.getFirstHeader("value").getValue()));
            response.getEntity().getContent().close();

            // Lets do one more check.
            response = client.execute(new HttpGet(url2));
            Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            Assert.assertEquals(4, Integer.parseInt(response.getFirstHeader("value").getValue()));
            response.getEntity().getContent().close();
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    /**
     * Test if the authentication status is successfully propagated between cluster nodes.
     *
     * @param baseURL1
     * @param baseURL2
     * @throws IOException when an HTTP client problem occurs
     */
    @Test
    @InSequence(4)
    @OperateOnDeployment(DEPLOYMENT_1)
    public void testAuthnPropagation(
            @ArquillianResource(SimpleSecuredServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1,
            @ArquillianResource(SimpleSecuredServlet.class) @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2) throws IOException {
        DefaultHttpClient client = HttpClientUtils.relaxedCookieHttpClient();

        final String servletPath = SimpleSecuredServlet.SERVLET_PATH.substring(1); // remove the leading slash

        try {
            // make request to a protected resource without authentication
            final HttpGet httpGet1 = new HttpGet(baseURL1.toString() + servletPath);
            HttpResponse response = client.execute(httpGet1);
            int statusCode = response.getStatusLine().getStatusCode();
            assertEquals("Unexpected HTTP response status code.", HttpServletResponse.SC_UNAUTHORIZED, statusCode);
            EntityUtils.consume(response.getEntity());

            // set credentials and retry the request
            final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("user1", "password1");
            client.getCredentialsProvider().setCredentials(new AuthScope(baseURL1.getHost(), baseURL1.getPort()), credentials);
            response = client.execute(httpGet1);
            statusCode = response.getStatusLine().getStatusCode();
            assertEquals("Unexpected HTTP response status code.", HttpServletResponse.SC_OK, statusCode);
            EntityUtils.consume(response.getEntity());

            waitForReplication(GRACE_TIME_TO_REPLICATE);

            // check on the 2nd server
            final HttpGet httpGet2 = new HttpGet(baseURL2.toString() + servletPath);
            response = client.execute(httpGet2);
            Assert.assertEquals("Unexpected HTTP response status code.", HttpServletResponse.SC_OK, response.getStatusLine()
                    .getStatusCode());
            EntityUtils.consume(response.getEntity());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    /**
     * Test that a session is gracefully served when a clustered application is undeployed.
     */
    @Test
    @InSequence(5)
    public void testGracefulServeOnUndeploy(
            @ArquillianResource(SimpleServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1)
            throws IllegalStateException, IOException, InterruptedException, Exception {
        this.abstractGracefulServe(baseURL1, true);
    }

    /**
     * Test that a session is gracefully served when clustered AS instanced is shutdown.
     */
    @Test
    @InSequence(6)
    public void testGracefulServeOnShutdown(
            @ArquillianResource(SimpleServlet.class) @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1)
            throws IllegalStateException, IOException, InterruptedException, Exception {
        this.abstractGracefulServe(baseURL1, false);
    }

    private void abstractGracefulServe(URL baseURL1, boolean undeployOnly) throws IllegalStateException, IOException,
            InterruptedException, Exception {

        final DefaultHttpClient client = HttpClientUtils.relaxedCookieHttpClient();
        String url1 = baseURL1.toString() + SimpleServlet.URL;

        // Make sure a normal request will succeed
        HttpResponse response = client.execute(new HttpGet(url1));
        Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
        response.getEntity().getContent().close();

        // Send a long request - in parallel
        String longRunningUrl = url1 + "?" + SimpleServlet.REQUEST_DURATION_PARAM + "=" + REQUEST_DURATION;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<HttpResponse> future = executor.submit(new RequestTask(client, longRunningUrl));

        // Make sure long request has started
        Thread.sleep(1000);

        if (undeployOnly) {
            // Undeploy the app only.
            undeploy(DEPLOYMENT_1);
        } else {
            // Shutdown server.
            stop(CONTAINER_1);
        }

        // Get result of long request
        // This request should succeed since it initiated before server shutdown
        try {
            response = future.get();
            Assert.assertEquals("Request should succeed since it initiated before undeply or shutdown.",
                    HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
            response.getEntity().getContent().close();
        } catch (ExecutionException e) {
            e.printStackTrace(System.err);
            Assert.fail(e.getCause().getMessage());
        }

        if (undeployOnly) {
            // If we are only undeploying, then subsequent requests should return 404.
            response = client.execute(new HttpGet(url1));
            Assert.assertEquals("If we are only undeploying, then subsequent requests should return 404.",
                    HttpServletResponse.SC_NOT_FOUND, response.getStatusLine().getStatusCode());
            response.getEntity().getContent().close();
        }

        // Cleanup after test.
        if (undeployOnly) {
            // Redeploy the app only.
            deploy(DEPLOYMENT_1);
        } else {
            // Startup server.
            start(CONTAINER_1);
        }
    }

    /**
     * Request task to request a long running URL and then undeploy / shutdown the server.
     */
    private class RequestTask implements Callable<HttpResponse> {

        private HttpClient client;
        private String url;

        RequestTask(HttpClient client, String url) {
            this.client = client;
            this.url = url;
        }

        @Override
        public HttpResponse call() throws Exception {
            try {
                return client.execute(new HttpGet(url));
            } finally {
                client.getConnectionManager().closeExpiredConnections();
            }
        }
    }
}
