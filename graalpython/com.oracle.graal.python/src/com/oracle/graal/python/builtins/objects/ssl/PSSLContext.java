package com.oracle.graal.python.builtins.objects.ssl;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.SSLContext;

import com.oracle.graal.python.builtins.modules.SSLModuleBuiltins;
import static com.oracle.graal.python.builtins.modules.SSLModuleBuiltins.LOGGER;
import static com.oracle.graal.python.builtins.modules.SSLModuleBuiltins.X509_V_FLAG_CRL_CHECK;
import static com.oracle.graal.python.builtins.modules.SSLModuleBuiltins.X509_V_FLAG_CRL_CHECK_ALL;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import static java.security.cert.PKIXRevocationChecker.Option.NO_FALLBACK;
import static java.security.cert.PKIXRevocationChecker.Option.ONLY_END_ENTITY;
import static java.security.cert.PKIXRevocationChecker.Option.PREFER_CRLS;
import java.security.cert.X509CRL;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

public final class PSSLContext extends PythonBuiltinObject {
    private final SSLMethod method;
    private final SSLContext context;
    private boolean checkHostname;
    private int verifyMode;
    private SSLCipher[] ciphers;
    private long options;
    private SSLProtocol minimumVersion;
    private SSLProtocol maximumVersion;

    private int verifyFlags;

    private String[] alpnProtocols;

    private KeyStore keystore;
    private Set<X509CRL> crls;

    private char[] password = PythonUtils.EMPTY_CHAR_ARRAY;

    public PSSLContext(Object cls, Shape instanceShape, SSLMethod method, int verifyFlags, boolean checkHostname, int verifyMode, SSLContext context) {
        super(cls, instanceShape);
        assert method != null;
        this.method = method;
        this.context = context;
        this.verifyFlags = verifyFlags;
        this.checkHostname = checkHostname;
        this.verifyMode = verifyMode;
        this.ciphers = SSLModuleBuiltins.defaultCiphers;
        LOGGER.fine(() -> String.format("PSSLContext() method: %s, verifyMode: %d, verifyFlags: %d, checkHostname: %b", method, verifyMode, verifyFlags, checkHostname));
    }

    @TruffleBoundary
    public KeyStore getKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        if (keystore == null) {
            keystore = KeyStore.getInstance("JKS");
            keystore.load(null);
        }
        return keystore;
    }

    public SSLMethod getMethod() {
        return method;
    }

    @TruffleBoundary
    void setCertificateEntries(Collection<? extends Object> list) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        for (Object obj : list) {
            if (obj instanceof X509Certificate) {
                X509Certificate cert = (X509Certificate) obj;
                getKeyStore().setCertificateEntry(CertUtils.getAlias(cert), cert);
            } else if (obj instanceof X509CRL) {
                getCRLs().add((X509CRL) obj);
            } else {
                throw new IllegalStateException("expected X509Certificate or X509CRL but got " + obj.getClass().getName());
            }
        }
    }

    @TruffleBoundary
    private Set<X509CRL> getCRLs() {
        if (crls == null) {
            crls = new HashSet<>();
        }
        return crls;
    }

    void setKeyEntry(PrivateKey pk, char[] password, X509Certificate[] certs) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        this.password = password;
        getKeyStore().setKeyEntry(CertUtils.getAlias(pk), pk, password, certs);
    }

    void init() throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException, InvalidAlgorithmParameterException {
        TrustManager[] tms = null;
        KeyManager[] kms = null;
        if (verifyMode == SSLModuleBuiltins.SSL_CERT_NONE) {
            tms = new TrustManager[]{new CertNoneTrustManager()};
            LOGGER.fine("PSSLContext.init() using SSL_CERT_NONE TrustManager");
        }
        if (keystore != null && keystore.size() > 0) {
            if (tms == null) {
                TrustManagerFactory tmf = getTrustManagerFactory();
                tms = tmf.getTrustManagers();
                if (verifyMode == SSLModuleBuiltins.SSL_CERT_OPTIONAL) {
                    LOGGER.fine("PSSLContext.init() using SSL_CERT_OPTIONAL TrustManager");
                    List<TrustManager> l = new ArrayList<>(tms.length);
                    for (TrustManager tm : tms) {
                        if (tm instanceof X509ExtendedTrustManager) {
                            l.add(new DelegateTrustManager((X509ExtendedTrustManager) tm, true));
                        }
                    }
                    tms = l.toArray(new TrustManager[l.size()]);
                }
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keystore, password);
            kms = kmf.getKeyManagers();
        }
        context.init(kms, tms, null);
    }

    private TrustManagerFactory getTrustManagerFactory() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        boolean crlCheck = (verifyFlags & X509_V_FLAG_CRL_CHECK) != 0;
        boolean crlCheckAll = (verifyFlags & X509_V_FLAG_CRL_CHECK_ALL) != 0;
        LOGGER.fine(() -> String.format("PSSLContext.getTrustManagerFactory() crlCheck: %b, crlCheckAll: %b", crlCheck, crlCheckAll));
        if (crlCheck || crlCheckAll) {
            PKIXRevocationChecker rc = (PKIXRevocationChecker) CertPathBuilder.getInstance("PKIX").getRevocationChecker();
            EnumSet<PKIXRevocationChecker.Option> opt = EnumSet.of(PREFER_CRLS, NO_FALLBACK);
            if (crlCheck) {
                opt.add(ONLY_END_ENTITY);
            }
            rc.setOptions(opt);
            PKIXBuilderParameters params = new PKIXBuilderParameters(keystore, new X509CertSelector());
            params.addCertPathChecker(rc);
            if (crls != null && !crls.isEmpty()) {
                CertStore certStores = CertStore.getInstance("Collection", new CollectionCertStoreParameters(crls));
                params.addCertStore(certStores);
                LOGGER.fine("PSSLContext.getTrustManagerFactory() adding crls");
            }
            tmf.init(new CertPathTrustManagerParameters(params));
        } else {
            tmf.init(keystore);
        }
        return tmf;
    }

    public SSLContext getContext() {
        return context;
    }

    public boolean getCheckHostname() {
        return checkHostname;
    }

    public void setCheckHostname(boolean checkHostname) {
        LOGGER.fine(() -> String.format("PSSLContext.setCheckHostname: %b", checkHostname));
        this.checkHostname = checkHostname;
    }

    int getVerifyMode() {
        return verifyMode;
    }

    void setVerifyMode(int verifyMode) {
        assert verifyMode == SSLModuleBuiltins.SSL_CERT_NONE || verifyMode == SSLModuleBuiltins.SSL_CERT_OPTIONAL || verifyMode == SSLModuleBuiltins.SSL_CERT_REQUIRED;
        LOGGER.fine(() -> String.format("PSSLContext.setVerifyMode: %d", verifyMode));
        this.verifyMode = verifyMode;
    }

    @TruffleBoundary
    public List<SSLCipher> computeEnabledCiphers(SSLEngine engine) {
        // We use the supported cipher suites to avoid errors, but the JDK provider will do
        // additional filtering based on the security policy before the communication starts
        Set<String> allowedCiphers = new HashSet<>(Arrays.asList(engine.getSupportedCipherSuites()));
        List<SSLCipher> enabledCiphers = new ArrayList<>(ciphers.length);
        for (SSLCipher cipher : ciphers) {
            if (allowedCiphers.contains(cipher.name())) {
                enabledCiphers.add(cipher);
            }
        }
        return enabledCiphers;
    }

    public void setCiphers(SSLCipher[] ciphers) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("PSSLContext.setCiphers:");
            for (SSLCipher c : ciphers) {
                LOGGER.fine(() -> String.format("\t", c));
            }
        }
        this.ciphers = ciphers;
    }

    public long getOptions() {
        return options;
    }

    public void setOptions(long options) {
        LOGGER.fine(() -> String.format("PSSLContext.setOptions: %d", options));
        this.options = options;
    }

    int getVerifyFlags() {
        return verifyFlags;
    }

    void setVerifyFlags(int flags) {
        LOGGER.fine(() -> String.format("PSSLContext.setVerifyFlags: %d", flags));
        this.verifyFlags = flags;
    }

    public String[] getAlpnProtocols() {
        return alpnProtocols;
    }

    public void setAlpnProtocols(String[] alpnProtocols) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("PSSLContext.setAlpnProtocols:");
            for (String p : alpnProtocols) {
                LOGGER.fine(() -> String.format("\t", p));
            }
        }
        this.alpnProtocols = alpnProtocols;
    }

    public SSLProtocol getMinimumVersion() {
        return minimumVersion;
    }

    public void setMinimumVersion(SSLProtocol minimumVersion) {
        LOGGER.fine(() -> String.format("PSSLContext.setMinimumVersion: %s", minimumVersion));
        this.minimumVersion = minimumVersion;
    }

    public SSLProtocol getMaximumVersion() {
        return maximumVersion;
    }

    public void setMaximumVersion(SSLProtocol maximumVersion) {
        LOGGER.fine(() -> String.format("PSSLContext.setMaximumVersion: %s", maximumVersion));
        this.maximumVersion = maximumVersion;
    }

    public boolean allowsProtocol(SSLProtocol protocol) {
        boolean disabledByOption = (options & protocol.getDisableOption()) == 0;
        boolean inMinBound = minimumVersion == null || protocol.getId() >= minimumVersion.getId();
        boolean inMaxBound = maximumVersion == null || protocol.getId() <= maximumVersion.getId();
        return inMinBound && inMaxBound && disabledByOption && method.allowsProtocol(protocol);
    }

    @TruffleBoundary
    public String[] computeEnabledProtocols() {
        List<SSLProtocol> supportedProtocols = SSLModuleBuiltins.getSupportedProtocols();
        List<String> list = new ArrayList<>(supportedProtocols.size());
        for (SSLProtocol protocol : supportedProtocols) {
            if (allowsProtocol(protocol)) {
                String name = protocol.getName();
                list.add(name);
            }
        }
        return list.toArray(new String[0]);
    }

    private static class CertNoneTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static class DelegateTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager delegate;
        /*
         * if in server mode and OPTIONAL, then check certificates only if some provided.
         */
        private final boolean certOptional;

        public DelegateTrustManager(X509ExtendedTrustManager delegate, boolean certOptional) {
            this.delegate = delegate;
            this.certOptional = certOptional;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (certOptional && (chain == null || chain.length == 0)) {
                return;
            }
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String string, Socket socket) throws CertificateException {
            if (certOptional && (chain == null || chain.length == 0)) {
                return;
            }
            delegate.checkClientTrusted(chain, string, socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String string, SSLEngine ssle) throws CertificateException {
            if (certOptional && (chain == null || chain.length == 0)) {
                return;
            }
            delegate.checkClientTrusted(chain, string, ssle);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String string, Socket socket) throws CertificateException {
            delegate.checkServerTrusted(chain, string, socket);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String string, SSLEngine ssle) throws CertificateException {
            delegate.checkServerTrusted(chain, string, ssle);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }

}
