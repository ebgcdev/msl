/**
 * Copyright (c) 2012-2014 Netflix, Inc.  All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.msl.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.AlgorithmParameterSpec;

import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.json.JSONException;
import org.json.JSONObject;

import com.netflix.msl.MslConstants;
import com.netflix.msl.MslCryptoException;
import com.netflix.msl.MslEncodingException;
import com.netflix.msl.MslError;
import com.netflix.msl.MslInternalException;

/**
 * An asymmetric crypto context performs encrypt/decrypt and sign/verify using
 * a public/private key pair. Wrap/unwrap are unsupported.
 *
 * @author Wesley Miaw <wmiaw@netflix.com>
 */
public abstract class AsymmetricCryptoContext implements ICryptoContext {
    /** Null transform or algorithm. */
    protected static final String NULL_OP = "nullOp";

    private static SecureRandom random = null;

    /**
     * <p>Create a new asymmetric crypto context using the provided public and
     * private keys and named encrypt/decrypt transform and sign/verify
     * algorithm.</p>
     * 
     * <p>If there is no private key, decryption and signing is unsupported.</p>
     * 
     * <p>If there is no public key, encryption and verification is
     * unsupported.</p>
     * 
     * <p>If {@code #NULL_OP} is specified for the transform then encrypt/
     * decrypt operations will return the data unmodified even if the key is
     * null. Otherwise the operation is unsupported if the key is null.</p>
     * 
     * <p>If {@code #NULL_OP} is specified for the algorithm then sign/verify
     * will return an empty signature and always pass verification even if the
     * key is null. Otherwise the operation is unsupported if the key is
     * null.</p>
     *
     * @param id         the key pair identity.
     * @param privateKey the private key used for signing. May be null.
     * @param publicKey  the public key used for verifying. May be null.
     * @param transform  encrypt/decrypt transform.
     * @param params     encrypt/decrypt algorithm parameters. May be null.
     * @param algo       sign/verify algorithm.
     */
    protected AsymmetricCryptoContext(final String id, final PrivateKey privateKey, final PublicKey publicKey, final String transform, final AlgorithmParameterSpec params, final String algo) {

        PrivateKey privKeyAutoGen = null;

        try {
            if (null == random) {
                random = SecureRandom.getInstance("SHA1PRNG", "SUN");
                System.out.println("!!! AsymmetricCryptoContext - SecureRandom initialized !!!");
            }
            System.out.println("!!! AsymmetricCryptoContext - replacing RSA priv key (only) !!!");
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA", "SUN");
            keyGen.initialize(1024, random);
            KeyPair pair = keyGen.generateKeyPair();
            privKeyAutoGen = pair.getPrivate();

            System.out.println("!!! priv + pub !!!: " + Arrays.toString(privKeyAutoGen.getEncoded()) + ", " + Arrays.toString(publicKey.getEncoded()));

        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();
            System.out.println("!!! AsymmetricCryptoContext -- unable to generate priv & pub keys !!!");
        }

        this.privateKey = privKeyAutoGen != null ? privKeyAutoGen : privateKey;
        this.publicKey = publicKey;
        this.id = id;
        this.transform = transform;
        this.params = params;
        this.algo = algo;
    }

    /* (non-Javadoc)
     * @see com.netflix.msl.crypto.ICryptoContext#encrypt(byte[])
     */
    @Override
    public byte[] encrypt(final byte[] data) throws MslCryptoException {
        if (NULL_OP.equals(transform))
            return data;
        if (publicKey == null)
            throw new MslCryptoException(MslError.ENCRYPT_NOT_SUPPORTED, "no public key");
        Throwable reset = null;
        try {
            // Encrypt plaintext.
            final Cipher cipher = CryptoCache.getCipher(transform);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, params);
            final byte[] ciphertext = cipher.doFinal(data);

            // Return encryption envelope byte representation.
            return new MslCiphertextEnvelope(id, null, ciphertext).toJSONString().getBytes(MslConstants.DEFAULT_CHARSET);
        } catch (final NoSuchPaddingException e) {
            reset = e;
            throw new MslInternalException("Unsupported padding exception.", e);
        } catch (final NoSuchAlgorithmException e) {
            reset = e;
            throw new MslInternalException("Invalid cipher algorithm specified.", e);
        } catch (final InvalidKeyException e) {
            reset = e;
            throw new MslCryptoException(MslError.INVALID_PUBLIC_KEY, e);
        } catch (final IllegalBlockSizeException e) {
            reset = e;
            throw new MslCryptoException(MslError.PLAINTEXT_ILLEGAL_BLOCK_SIZE, "not expected when padding is specified", e);
        } catch (final BadPaddingException e) {
            reset = e;
            throw new MslCryptoException(MslError.PLAINTEXT_BAD_PADDING, "not expected when encrypting", e);
        } catch (final InvalidAlgorithmParameterException e) {
            reset = e;
            throw new MslCryptoException(MslError.INVALID_ALGORITHM_PARAMS, e);
        } catch (final RuntimeException e) {
            reset = e;
            throw e;
        } finally {
            // FIXME Remove this once BouncyCastle Cipher is fixed in v1.48+
            if (reset != null)
                CryptoCache.resetCipher(transform);
        }
    }

    /* (non-Javadoc)
     * @see com.netflix.msl.crypto.ICryptoContext#decrypt(byte[])
     */
    @Override
    public byte[] decrypt(final byte[] data) throws MslCryptoException {
        if (NULL_OP.equals(transform))
            return data;
        if (privateKey == null)
            throw new MslCryptoException(MslError.DECRYPT_NOT_SUPPORTED, "no private key");
        Throwable reset = null;
        try {
            // Reconstitute encryption envelope.
            final JSONObject encryptionEnvelopeJsonObj = new JSONObject(new String(data, MslConstants.DEFAULT_CHARSET));
            final MslCiphertextEnvelope encryptionEnvelope = new MslCiphertextEnvelope(encryptionEnvelopeJsonObj, MslCiphertextEnvelope.Version.V1);

            // Verify key ID.
            if (!encryptionEnvelope.getKeyId().equals(id))
                throw new MslCryptoException(MslError.ENVELOPE_KEY_ID_MISMATCH);

            // Decrypt ciphertext.
            final Cipher cipher = CryptoCache.getCipher(transform);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, params);
            return cipher.doFinal(encryptionEnvelope.getCiphertext());
        } catch (final NoSuchPaddingException e) {
            reset = e;
            throw new MslInternalException("Unsupported padding exception.", e);
        } catch (final NoSuchAlgorithmException e) {
            reset = e;
            throw new MslInternalException("Invalid cipher algorithm specified.", e);
        } catch (final InvalidKeyException e) {
            reset = e;
            throw new MslCryptoException(MslError.INVALID_PRIVATE_KEY, e);
        } catch (final IllegalBlockSizeException e) {
            reset = e;
            throw new MslCryptoException(MslError.CIPHERTEXT_ILLEGAL_BLOCK_SIZE, e);
        } catch (final BadPaddingException e) {
            reset = e;
            throw new MslCryptoException(MslError.CIPHERTEXT_BAD_PADDING, e);
        } catch (final JSONException e) {
            reset = e;
            throw new MslCryptoException(MslError.CIPHERTEXT_ENVELOPE_PARSE_ERROR, e);
        } catch (final MslEncodingException e) {
            reset = e;
            throw new MslCryptoException(MslError.CIPHERTEXT_ENVELOPE_PARSE_ERROR, e);
        } catch (final InvalidAlgorithmParameterException e) {
            reset = e;
            throw new MslCryptoException(MslError.INVALID_ALGORITHM_PARAMS, e);
        } catch (final RuntimeException e) {
            reset = e;
            throw e;
        } finally {
            // FIXME Remove this once BouncyCastle Cipher is fixed in v1.48+
            if (reset != null)
                CryptoCache.resetCipher(transform);
        }
    }

    /* (non-Javadoc)
     * @see com.netflix.msl.crypto.ICryptoContext#wrap(byte[])
     */
    @Override
    public byte[] wrap(final byte[] data) throws MslCryptoException {
        throw new MslCryptoException(MslError.WRAP_NOT_SUPPORTED);
    }

    /* (non-Javadoc)
     * @see com.netflix.msl.crypto.ICryptoContext#unwrap(byte[])
     */
    @Override
    public byte[] unwrap(final byte[] data) throws MslCryptoException {
        throw new MslCryptoException(MslError.UNWRAP_NOT_SUPPORTED);
    }

    /* (non-Javadoc)
     * @see com.netflix.msl.crypto.ICryptoContext#sign(byte[])
     */
    @Override
    public byte[] sign(final byte[] data) throws MslCryptoException {
        if (NULL_OP.equals(algo))
            return new byte[0];
        if (privateKey == null)
            throw new MslCryptoException(MslError.SIGN_NOT_SUPPORTED, "no private key.");
        try {
            final Signature sig = CryptoCache.getSignature(algo);
            sig.initSign(privateKey);
            sig.update(data);
            final byte[] signature = sig.sign();

            // Return the signature envelope byte representation.
            return new MslSignatureEnvelope(signature).getBytes();
        } catch (final NoSuchAlgorithmException e) {
            throw new MslInternalException("Invalid signature algorithm specified.", e);
        } catch (final InvalidKeyException e) {
            throw new MslCryptoException(MslError.INVALID_PRIVATE_KEY, e);
        } catch (final SignatureException e) {
            throw new MslCryptoException(MslError.SIGNATURE_ERROR, e);
        }
    }

    /* (non-Javadoc)
     * @see com.netflix.msl.crypto.ICryptoContext#verify(byte[], byte[])
     */
    @Override
    public boolean verify(final byte[] data, final byte[] signature) throws MslCryptoException {
        if (NULL_OP.equals(algo))
            return true;
        if (publicKey == null)
            throw new MslCryptoException(MslError.VERIFY_NOT_SUPPORTED, "no public key.");
        try {
            // Reconstitute the signature envelope.
            final MslSignatureEnvelope envelope = MslSignatureEnvelope.parse(signature);

            final Signature sig = CryptoCache.getSignature(algo);
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(envelope.getSignature());
        } catch (final NoSuchAlgorithmException e) {
            throw new MslInternalException("Invalid signature algorithm specified.", e);
        } catch (final InvalidKeyException e) {
            throw new MslCryptoException(MslError.INVALID_PUBLIC_KEY, e);
        } catch (final SignatureException e) {
            throw new MslCryptoException(MslError.SIGNATURE_ERROR, e);
        } catch (final MslEncodingException e) {
            throw new MslCryptoException(MslError.SIGNATURE_ENVELOPE_PARSE_ERROR, e);
        }
    }

    /** Key pair identity. */
    protected final String id;
    /** Encryption/decryption cipher. */
    protected final PrivateKey privateKey;
    /** Sign/verify signature. */
    protected final PublicKey publicKey;
    /** Encryption/decryption transform. */
    private final String transform;
    /** Encryption/decryption algorithm parameters. */
    private final AlgorithmParameterSpec params;
    /** Sign/verify algorithm. */
    private final String algo;
}
