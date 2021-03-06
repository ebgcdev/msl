/**
 * Copyright (c) 2015 Netflix, Inc.  All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.msl.entityauth;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.netflix.msl.MslCryptoException;
import com.netflix.msl.MslEncodingException;
import com.netflix.msl.MslEntityAuthException;
import com.netflix.msl.MslError;
import com.netflix.msl.crypto.ICryptoContext;
import com.netflix.msl.test.ExpectedMslException;
import com.netflix.msl.util.JsonUtils;
import com.netflix.msl.util.MockAuthenticationUtils;
import com.netflix.msl.util.MockMslContext;

/**
 * Unauthenticated suffixed authentication factory unit tests.
 * 
 * @author Wesley Miaw <wmiaw@netflix.com>
 */
public class UnauthenticatedSuffixedAuthenticationFactoryTest {
    /** JSON key entity root. */
    private static final String KEY_ROOT = "root";
    
    @Rule
    public ExpectedMslException thrown = ExpectedMslException.none();
    
    private static final String UNAUTHENTICATED_ROOT = "MOCKUNAUTH-ROOT";
    private static final String UNAUTHENTICATED_SUFFIX = "MOCKUNAUTH-SUFFIX";

    /** Authentication utilities. */
    private static MockAuthenticationUtils authutils;
    
    @BeforeClass
    public static void setup() throws MslEncodingException, MslCryptoException {
        ctx = new MockMslContext(EntityAuthenticationScheme.NONE, false);
        authutils = new MockAuthenticationUtils();
        factory = new UnauthenticatedSuffixedAuthenticationFactory(authutils);
        ctx.addEntityAuthenticationFactory(factory);
    }
    
    @AfterClass
    public static void teardown() {
        factory = null;
        ctx = null;
    }
    
    @After
    public void reset() {
        authutils.reset();
    }
    
    @Test
    public void createData() throws MslCryptoException, MslEncodingException, MslEntityAuthException, JSONException {
        final UnauthenticatedSuffixedAuthenticationData data = new UnauthenticatedSuffixedAuthenticationData(UNAUTHENTICATED_ROOT, UNAUTHENTICATED_SUFFIX);
        final JSONObject entityAuthJO = data.getAuthData();
        
        final EntityAuthenticationData authdata = factory.createData(ctx, entityAuthJO);
        assertNotNull(authdata);
        assertTrue(authdata instanceof UnauthenticatedSuffixedAuthenticationData);
        
        final JSONObject dataJo = new JSONObject(data.toJSONString());
        final JSONObject authdataJo = new JSONObject(authdata.toJSONString());
        assertTrue(JsonUtils.equals(dataJo, authdataJo));
    }
    
    @Test
    public void encodeException() throws MslCryptoException, MslEncodingException, MslEntityAuthException {
        thrown.expect(MslEncodingException.class);
        thrown.expectMslError(MslError.JSON_PARSE_ERROR);

        final UnauthenticatedSuffixedAuthenticationData data = new UnauthenticatedSuffixedAuthenticationData(UNAUTHENTICATED_ROOT, UNAUTHENTICATED_SUFFIX);
        final JSONObject entityAuthJO = data.getAuthData();
        entityAuthJO.remove(KEY_ROOT);
        factory.createData(ctx, entityAuthJO);
    }
    
    @Test
    public void cryptoContext() throws MslCryptoException, MslEntityAuthException {
        final UnauthenticatedSuffixedAuthenticationData data = new UnauthenticatedSuffixedAuthenticationData(UNAUTHENTICATED_ROOT, UNAUTHENTICATED_SUFFIX);
        final ICryptoContext cryptoContext = factory.getCryptoContext(ctx, data);
        assertNotNull(cryptoContext);
    }
    
    @Test
    public void revoked() throws MslCryptoException, MslEntityAuthException {
        thrown.expect(MslEntityAuthException.class);
        thrown.expectMslError(MslError.ENTITY_REVOKED);

        authutils.revokeEntity(UNAUTHENTICATED_ROOT);
        final UnauthenticatedSuffixedAuthenticationData data = new UnauthenticatedSuffixedAuthenticationData(UNAUTHENTICATED_ROOT, UNAUTHENTICATED_SUFFIX);
        factory.getCryptoContext(ctx, data);
    }
    
    /** MSL context. */
    private static MockMslContext ctx;
    /** Entity authentication factory. */
    private static EntityAuthenticationFactory factory;
}
