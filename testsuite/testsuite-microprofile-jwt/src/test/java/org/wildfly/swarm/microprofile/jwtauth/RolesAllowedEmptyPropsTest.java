/*
 *   Copyright 2018 Red Hat, Inc, and individual contributors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.wildfly.swarm.microprofile.jwtauth;

import java.io.InputStream;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.UrlAsset;
import org.jboss.shrinkwrap.impl.base.io.IOUtil;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.swarm.jaxrs.JAXRSArchive;

@RunWith(Arquillian.class)
public class RolesAllowedEmptyPropsTest {

    @Deployment
    public static Archive<?> createDeployment() throws Exception {
        JAXRSArchive deployment = ShrinkWrap.create(JAXRSArchive.class);
        deployment.addResource(RolesEndpointClassLevel.class);
        deployment.addResource(TestApplication.class);
        deployment.addAllDependencies();
        deployment.addAsResource("emptyRoles.properties");
        deployment.addAsResource("project-defaults.yml");
        URL url = RolesAllowedEmptyPropsTest.class.getResource("/publicKey.pem");
        deployment.addAsManifestResource(new UrlAsset(url), "/MP-JWT-SIGNER");
        return deployment;
    }

    @RunAsClient
    @Test
    public void testRolesAllowed() throws Exception {
        String uri = "http://localhost:8080/mpjwt/rolesClass";
        WebTarget target = ClientBuilder.newClient().target(uri).queryParam("input", "hello");
        String response = target.request(MediaType.TEXT_PLAIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("Echoer")).get(String.class);
        Assert.assertEquals(response, "hello, user=jdoe@example.com");
    }
    
    @RunAsClient
    @Test
    public void testRolesNotAllowed() throws Exception {
        String uri = "http://localhost:8080/mpjwt/rolesClass";
        WebTarget target = ClientBuilder.newClient().target(uri).queryParam("input", "hello");
        Response resp = target.request(MediaType.TEXT_PLAIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken("Echoer2")).get();
        Assert.assertEquals(Response.Status.FORBIDDEN, resp.getStatusInfo());
    }
    
    private static String createToken(String groupName) throws Exception {
        String value = "{"
                + "\"iss\": \"https://server.example.com\","
                + "\"sub\": \"24400320\","
                + "\"exp\": " + System.currentTimeMillis() + 36000 + ","
                + "\"upn\": \"jdoe@example.com\","
                + "\"groups\": ["
                + " \"" + groupName + "\"" 
                + " ]"
                + "}";
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(value);
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKey(getPrivateKey());
        return jws.getCompactSerialization();
    }
    private static PrivateKey getPrivateKey() throws Exception {
        InputStream is = RolesAllowedEmptyPropsTest.class.getResourceAsStream("/privateKey.pem");
        String pemEncoded = removeBeginEnd(new String(IOUtil.asByteArray(is)));
        byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(pemEncoded);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privKey = kf.generatePrivate(keySpec);
        return privKey;
    }
    private static String removeBeginEnd(String pem) {
        pem = pem.replaceAll("-----BEGIN (.*)-----", "");
        pem = pem.replaceAll("-----END (.*)----", "");
        pem = pem.replaceAll("\r\n", "");
        pem = pem.replaceAll("\n", "");
        return pem.trim();
    }

}
