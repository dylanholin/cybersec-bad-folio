package com.devfolio.util;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

public final class UrlValidator {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
        "raw.githubusercontent.com", "api.github.com", "github.com",
        "i.imgur.com", "avatars.githubusercontent.com");

    private static final long MAX_FETCH_SIZE = 2 * 1024 * 1024; // 2 Mo
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    private UrlValidator() {}

    /**
     * Valide ET récupère le contenu d'une URL en une seule opération atomique.
     *
     * Résout le DNS une seule fois, vérifie l'IP, puis se connecte vers cette IP
     * exacte (anti DNS rebinding). Le hostname original est préservé via le
     * header Host et SNI pour que le serveur TLS présente le bon certificat.
     *
     * Pourquoi HttpsURLConnection et non HttpClient (Java 11+) ?
     * HttpClient ne permet pas de se connecter vers une IP spécifique ni de
     * personnaliser SNI / HostnameVerifier sans un SSLContext custom complexe.
     * HttpsURLConnection est la seule API standard Java qui offre ce contrôle.
     */
    public static InputStream fetchContent(final String rawUrl) throws IOException {
        final URI uri = parseAndCheckScheme(rawUrl);
        final String host = uri.getHost();
        final InetAddress addr = resolveAndCheckIp(host);

        // Construction d'une URL vers l'IP résolue (anti DNS rebinding)
        // IPv6 : encapsuler dans des crochets [...] pour que l'URI soit valide
        final String ipLiteral = addr.getHostAddress().contains(":")
                ? "[" + addr.getHostAddress() + "]"
                : addr.getHostAddress();
        final URL url;
        try {
            url = new URI("https", null, ipLiteral, uri.getPort(),
                    uri.getPath(), uri.getQuery(), null).toURL();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL invalide", e);
        }

        final HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestProperty("Host", host);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);

        // SNI : SSLSocketFactory custom qui préserve le hostname original
        // pour que le serveur TLS présente le bon certificat.
        // HttpsURLConnection n'expose pas getSSLParameters()/setSSLParameters(),
        // la seule façon de configurer SNI est via un SSLSocketFactory custom.
        conn.setSSLSocketFactory(createSniSocketFactory(host));

        // HostnameVerifier : valider le certificat contre le hostname original,
        // pas contre l'IP (un certificat n'est jamais émis pour une IP)
        conn.setHostnameVerifier((h, session) ->
                HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session));

        return conn.getInputStream();
    }

    public static long getMaxFetchSize() {
        return MAX_FETCH_SIZE;
    }

    // --- Méthodes privées ---

    /**
     * Crée une SSLSocketFactory qui configure SNI avec le hostname original
     * sur chaque socket. Nécessaire car on se connecte vers une IP résolue
     * (anti DNS rebinding), mais le serveur TLS a besoin du hostname pour
     * présenter le bon certificat.
     */
    private static SSLSocketFactory createSniSocketFactory(final String hostname) {
        final SSLSocketFactory delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
        return new SSLSocketFactory() {
            @Override
            public Socket createSocket(final Socket s, final String host, final int port, final boolean autoClose) throws IOException {
                return configureSni((SSLSocket) delegate.createSocket(s, host, port, autoClose));
            }

            @Override
            public Socket createSocket(final String host, final int port) throws IOException {
                return configureSni((SSLSocket) delegate.createSocket(host, port));
            }

            @Override
            public Socket createSocket(final String host, final int port, final InetAddress localHost, final int localPort) throws IOException {
                return configureSni((SSLSocket) delegate.createSocket(host, port, localHost, localPort));
            }

            @Override
            public Socket createSocket(final InetAddress host, final int port) throws IOException {
                return configureSni((SSLSocket) delegate.createSocket(host, port));
            }

            @Override
            public Socket createSocket(final InetAddress address, final int port, final InetAddress localAddress, final int localPort) throws IOException {
                return configureSni((SSLSocket) delegate.createSocket(address, port, localAddress, localPort));
            }

            private SSLSocket configureSni(final SSLSocket socket) {
                final SSLParameters params = socket.getSSLParameters();
                params.setServerNames(List.of(new SNIHostName(hostname)));
                socket.setSSLParameters(params);
                return socket;
            }

            @Override
            public String[] getDefaultCipherSuites() {
                return delegate.getDefaultCipherSuites();
            }

            @Override
            public String[] getSupportedCipherSuites() {
                return delegate.getSupportedCipherSuites();
            }
        };
    }

    private static URI parseAndCheckScheme(final String rawUrl) {
        try {
            final URI uri = new URI(rawUrl);
            if (!"https".equals(uri.getScheme())) {
                throw new IllegalArgumentException("Seul HTTPS est autorisé");
            }
            final String host = uri.getHost();
            if (host == null || !ALLOWED_HOSTS.contains(host)) {
                throw new IllegalArgumentException("Domaine non autorisé : " + host);
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL invalide", e);
        }
    }

    private static InetAddress resolveAndCheckIp(final String host) {
        try {
            final InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                throw new IllegalArgumentException("Adresse IP interdite");
            }
            return addr;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Domaine introuvable : " + host, e);
        }
    }
}
