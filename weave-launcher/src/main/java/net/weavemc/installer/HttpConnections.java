package net.weavemc.installer;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * One place for every HTTPS request the installer makes. Connects directly first,
 * then through the Windows system proxy, retries transient failures, trusts both
 * the bundled Java root certificates and the Windows certificate store (so
 * antivirus or school proxies that re-sign HTTPS still work), and turns low-level
 * failures into messages a user can act on.
 */
final class HttpConnections {
    private static final int ATTEMPTS = 3;
    private static final Object FACTORY_LOCK = new Object();
    private static boolean factoryResolved;
    private static SSLSocketFactory socketFactory;

    private HttpConnections() {
    }

    /**
     * Opens the URL and waits for the response status line. Callers still check the
     * status code, read the body, and disconnect. Every attempt failing ends in a
     * ReleaseServerException whose message is meant for the user.
     */
    static HttpURLConnection connect(String url, int connectTimeout, int readTimeout)
            throws IOException {
        URL target = new URL(url);
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            List<Proxy> routes = attempt == 1 ? directOnly() : proxiesThenDirect(target);
            for (Proxy route : routes) {
                HttpURLConnection connection = null;
                try {
                    connection = open(target, route, connectTimeout, readTimeout);
                    int status = connection.getResponseCode();
                    if (isTransientStatus(status) && attempt < ATTEMPTS
                            && !isChallenge(connection)) {
                        last = new IOException("HTTP " + status + " from " + url);
                        InstallerLog.info("Attempt " + attempt + " via " + route
                                + " got HTTP " + status + " for " + url);
                        connection.disconnect();
                        continue;
                    }
                    return connection;
                } catch (ReleaseServerException exception) {
                    if (connection != null) {
                        connection.disconnect();
                    }
                    throw exception;
                } catch (IOException exception) {
                    if (connection != null) {
                        connection.disconnect();
                    }
                    last = exception;
                    InstallerLog.error("Attempt " + attempt + " via " + route
                            + " failed for " + url, exception);
                }
            }
            if (attempt < ATTEMPTS) {
                pause(attempt * 1500L);
            }
        }
        logEnvironment(target);
        throw describe(url, last);
    }

    /** Records what the network looks like from this machine, for the Logs window. */
    private static void logEnvironment(URL target) {
        StringBuilder report = new StringBuilder("Network diagnostics for " + target.getHost());
        try {
            for (InetAddress address : InetAddress.getAllByName(target.getHost())) {
                report.append("\n  resolves to ").append(address.getHostAddress());
            }
        } catch (IOException | RuntimeException exception) {
            report.append("\n  name lookup failed: ").append(exception);
        }
        for (String property : new String[] {"http.proxyHost", "http.proxyPort",
                "https.proxyHost", "https.proxyPort", "http.nonProxyHosts",
                "java.net.useSystemProxies"}) {
            String value = System.getProperty(property);
            if (value != null) {
                report.append("\n  ").append(property).append('=').append(value);
            }
        }
        for (String variable : new String[] {"JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS",
                "HTTPS_PROXY", "HTTP_PROXY"}) {
            String value = System.getenv(variable);
            if (value != null) {
                report.append("\n  ").append(variable).append('=').append(value);
            }
        }
        try {
            ProxySelector selector = ProxySelector.getDefault();
            if (selector != null) {
                report.append("\n  system proxy route: ").append(selector.select(target.toURI()));
            }
        } catch (RuntimeException | URISyntaxException exception) {
            report.append("\n  system proxy lookup failed: ").append(exception);
        }
        InstallerLog.info(report.toString());
    }

    /** Builds the user-facing failure for a non-2xx response. */
    static ReleaseServerException statusFailure(HttpURLConnection connection, String url)
            throws IOException {
        int status = connection.getResponseCode();
        String detail = "HTTP " + status + " for " + url;
        if (isChallenge(connection) || status == 403 || status == 429 || status == 503) {
            return new ReleaseServerException(
                    "The release server refused this connection (HTTP " + status + ").\n"
                            + "If you use a VPN or proxy, turn it off and click Retry. "
                            + "If it keeps happening, wait a minute and try again.",
                    new IOException(detail));
        }
        if (status == 404) {
            return new ReleaseServerException(
                    "The release server is missing a file it advertised (HTTP 404).\n"
                            + "A new release may be mid-upload. Click Retry in a minute.",
                    new IOException(detail));
        }
        return new ReleaseServerException(
                "The release server answered with HTTP " + status + ".\nClick Retry in a minute.",
                new IOException(detail));
    }

    /** Turns a low-level failure into a message the user can act on. */
    static ReleaseServerException describe(String url, IOException failure) {
        if (failure instanceof ReleaseServerException) {
            return (ReleaseServerException) failure;
        }
        String host;
        try {
            host = new URL(url).getHost();
        } catch (IOException exception) {
            host = url;
        }
        Throwable root = failure;
        while (root != null && root.getCause() != null) {
            root = root.getCause();
        }
        String detail = root == null ? "unknown failure"
                : root.getClass().getSimpleName()
                        + (root.getMessage() == null ? "" : ": " + root.getMessage());
        String advice;
        if (detail.contains("Unsupported or unrecognized SSL message")
                || detail.contains("plaintext connection")) {
            advice = "Something on this computer or network answered instead of the release server.\n"
                    + "This is usually a DNS filter or ad blocker, an entry for " + host
                    + " in the Windows hosts file, a proxy setting, or a security program's web filter. "
                    + "Turn those off or remove the entry, then click Retry. "
                    + "The Logs button shows which address this computer resolves the server to.";
        } else if (failure instanceof UnknownHostException) {
            advice = "Could not look up the release server (" + host + ").\n"
                    + "Check that this computer is online, then click Retry. "
                    + "If the internet works in a browser, a DNS filter or firewall is blocking it.";
        } else if (failure instanceof SocketTimeoutException
                || failure instanceof ConnectException
                || failure instanceof NoRouteToHostException
                || failure instanceof PortUnreachableException) {
            advice = "The release server did not answer.\n"
                    + "A firewall, VPN, proxy, or unstable connection is blocking it. "
                    + "Turn VPNs off, allow Fireball Fight Mods through the firewall, then click Retry.";
        } else if (failure instanceof SSLException || root instanceof CertificateException) {
            advice = "The secure connection to the release server could not be trusted.\n"
                    + "Antivirus HTTPS scanning, a school or work proxy, or a wrong system "
                    + "date and time can cause this. Check the clock, pause the antivirus web "
                    + "shield, then click Retry.";
        } else if (failure instanceof SocketException) {
            advice = "The connection to the release server was interrupted.\n"
                    + "Check the internet connection, then click Retry.";
        } else {
            advice = "The release server could not be reached.\nClick Retry in a moment.";
        }
        return new ReleaseServerException(advice + "\n\nTechnical detail: " + detail, failure);
    }

    private static HttpURLConnection open(URL target, Proxy route,
                                          int connectTimeout, int readTimeout) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) target.openConnection(route);
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "Fireball-Fight-Mods/" + Main.VERSION);
        connection.setRequestProperty("Accept", "*/*");
        if (connection instanceof HttpsURLConnection) {
            SSLSocketFactory factory = socketFactory();
            if (factory != null) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(factory);
            }
        }
        return connection;
    }

    private static boolean isTransientStatus(int status) {
        return status == 502 || status == 503 || status == 504 || status == 520
                || status == 521 || status == 522 || status == 523 || status == 524;
    }

    private static boolean isChallenge(HttpURLConnection connection) {
        String mitigated = connection.getHeaderField("cf-mitigated");
        return mitigated != null && mitigated.toLowerCase(Locale.ROOT).contains("challenge");
    }

    private static List<Proxy> directOnly() {
        List<Proxy> routes = new ArrayList<Proxy>(1);
        routes.add(Proxy.NO_PROXY);
        return routes;
    }

    private static List<Proxy> proxiesThenDirect(URL target) {
        List<Proxy> routes = new ArrayList<Proxy>();
        try {
            ProxySelector selector = ProxySelector.getDefault();
            if (selector != null) {
                for (Proxy proxy : selector.select(target.toURI())) {
                    if (proxy != null && proxy.type() != Proxy.Type.DIRECT
                            && !routes.contains(proxy)) {
                        routes.add(proxy);
                    }
                }
            }
        } catch (RuntimeException | URISyntaxException ignored) {
            // No usable system proxy; fall through to a direct connection.
        }
        routes.add(Proxy.NO_PROXY);
        return routes;
    }

    private static void pause(long milliseconds) throws InterruptedIOException {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Cancelled while waiting to retry.");
        }
    }

    private static SSLSocketFactory socketFactory() {
        synchronized (FACTORY_LOCK) {
            if (!factoryResolved) {
                factoryResolved = true;
                socketFactory = buildSocketFactory();
            }
            return socketFactory;
        }
    }

    /**
     * Combines the bundled Java root store with the Windows root store. Returns null
     * (use the JDK default) when the Windows store adds nothing or cannot be read.
     */
    private static SSLSocketFactory buildSocketFactory() {
        List<X509TrustManager> managers = new ArrayList<X509TrustManager>();
        addTrustManagers(managers, null, "bundled");
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            try {
                KeyStore windows = KeyStore.getInstance("Windows-ROOT");
                windows.load(null, null);
                addTrustManagers(managers, windows, "Windows-ROOT");
            } catch (Exception exception) {
                InstallerLog.info("Windows certificate store unavailable: " + exception);
            }
        }
        if (managers.size() < 2) {
            InstallerLog.info("TLS trust: bundled Java roots only");
            return null;
        }
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {new CompositeTrustManager(managers)}, null);
            InstallerLog.info("TLS trust: bundled Java roots plus the Windows certificate store");
            return context.getSocketFactory();
        } catch (Exception exception) {
            InstallerLog.error("Could not build the combined trust store", exception);
            return null;
        }
    }

    private static void addTrustManagers(List<X509TrustManager> output, KeyStore store,
                                         String label) {
        try {
            TrustManagerFactory factory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            for (TrustManager manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager) {
                    output.add((X509TrustManager) manager);
                }
            }
        } catch (Exception exception) {
            InstallerLog.info("Trust store " + label + " unavailable: " + exception);
        }
    }

    /** Accepts a chain when any of the wrapped trust managers accepts it. */
    private static final class CompositeTrustManager implements X509TrustManager {
        private final List<X509TrustManager> delegates;

        CompositeTrustManager(List<X509TrustManager> delegates) {
            this.delegates = delegates;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            CertificateException first = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    delegate.checkClientTrusted(chain, authType);
                    return;
                } catch (CertificateException exception) {
                    if (first == null) {
                        first = exception;
                    }
                }
            }
            throw first == null ? new CertificateException("No trust manager available") : first;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            CertificateException first = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    delegate.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException exception) {
                    if (first == null) {
                        first = exception;
                    }
                }
            }
            throw first == null ? new CertificateException("No trust manager available") : first;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> issuers = new ArrayList<X509Certificate>();
            for (X509TrustManager delegate : delegates) {
                for (X509Certificate issuer : delegate.getAcceptedIssuers()) {
                    issuers.add(issuer);
                }
            }
            return issuers.toArray(new X509Certificate[0]);
        }
    }
}
