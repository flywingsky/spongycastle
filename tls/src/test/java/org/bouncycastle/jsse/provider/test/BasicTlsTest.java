package org.bouncycastle.jsse.provider.test;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import junit.framework.TestCase;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

public class BasicTlsTest
    extends TestCase
{
    protected void setUp()
    {
        if (Security.getProvider("BC") == null)
        {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider("BCJSSE") == null)
        {
            Security.addProvider(new BouncyCastleJsseProvider());
        }
    }

    private static final String HOST = "localhost";
    private static final int PORT_NO = 9020;

    public static class SimpleClient
        implements TestProtocolUtil.BlockingCallable
    {
        private final KeyStore trustStore;
        private final CountDownLatch latch;

        public SimpleClient(KeyStore trustStore)
        {
            this.trustStore = trustStore;
            this.latch = new CountDownLatch(1);
        }

        public Object call()
            throws Exception
        {
            TrustManagerFactory trustMgrFact = TrustManagerFactory.getInstance("X509", "BCJSSE");

            trustMgrFact.init(trustStore);

            SSLContext clientContext = SSLContext.getInstance("TLS");

            clientContext.init(null, trustMgrFact.getTrustManagers(), SecureRandom.getInstance("DEFAULT", "BC"));

            SSLSocketFactory fact = clientContext.getSocketFactory();
            SSLSocket cSock = (SSLSocket)fact.createSocket(HOST, PORT_NO);

            TestProtocolUtil.doClientProtocol(cSock, "Hello");

            latch.countDown();

            return null;
        }

        public void await()
            throws InterruptedException
        {
            latch.await();
        }
    }

    public static class SimpleServer
        implements TestProtocolUtil.BlockingCallable
    {
        private final KeyStore serverStore;
        private final char[] keyPass;
        private final CountDownLatch latch;

        SimpleServer(KeyStore serverStore, char[] keyPass)
        {
            this.serverStore = serverStore;
            this.keyPass = keyPass;
            this.latch = new CountDownLatch(1);
        }

        public Object call()
            throws Exception
        {
            KeyManagerFactory keyMgrFact = KeyManagerFactory.getInstance("X509", "BCJSSE");

            keyMgrFact.init(serverStore, keyPass);

            SSLContext serverContext = SSLContext.getInstance("TLS", "BCJSSE");

            serverContext.init(keyMgrFact.getKeyManagers(), null, SecureRandom.getInstance("DEFAULT", "BC"));

            SSLServerSocketFactory fact = serverContext.getServerSocketFactory();
            SSLServerSocket sSock = (SSLServerSocket)fact.createServerSocket(PORT_NO);

            latch.countDown();

            SSLSocket sslSock = (SSLSocket)sSock.accept();
            sslSock.setUseClientMode(false);

            // TODO[jsse] Is this supposed to be a necessary call to get an SSL connection?
            sslSock.startHandshake();

            TestProtocolUtil.doServerProtocol(sslSock, "World");

            return null;
        }

        public void await()
            throws InterruptedException
        {
            latch.await();
        }
    }

    public void testBasicTlsConnection()
        throws Exception
    {
        char[] keyPass = "keyPassword".toCharArray();

        KeyPair caKeyPair = TestUtils.generateECKeyPair();

        X509Certificate caCert = TestUtils.generateRootCert(caKeyPair);

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setKeyEntry("server", caKeyPair.getPrivate(), keyPass, new X509Certificate[]{ caCert });

        KeyStore ts = KeyStore.getInstance("JKS");
        ts.load(null, null);
        ts.setCertificateEntry("ca", caCert);

        TestProtocolUtil.runClientAndServer(new SimpleServer(ks, keyPass), new SimpleClient(ts));
    }
}