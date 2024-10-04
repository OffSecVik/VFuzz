package vfuzz.network;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.protocol.HttpContext;
import vfuzz.config.ConfigAccessor;

/**
 * The {@code CustomRedirectStrategy} class extends the {@link DefaultRedirectStrategy} to provide
 * custom handling of HTTP redirects based on the application’s configuration.
 *
 * <p>This strategy allows redirects to be optionally followed based on a configuration value.
 * If redirects are disabled in the configuration, no redirect will be followed, even if the server
 * returns a redirect response. Additionally, the strategy checks for a "Location" header in the response,
 * which is required for following the redirect.
 */
public class CustomRedirectStrategy extends DefaultRedirectStrategy {

    /**
     * Determines if the given request should be redirected based on the HTTP response and context.
     *
     * <p>This method first checks the configuration to see if redirects are enabled. If redirects are
     * disabled in the configuration (via the "followRedirects" key), it returns {@code false}.
     * Otherwise, it falls back to the default redirect handling provided by {@link DefaultRedirectStrategy},
     * but adds an additional check to ensure that the "Location" header is present in the response.
     *
     * @param request  The original {@link HttpRequest} that was sent.
     * @param response The {@link HttpResponse} received from the server.
     * @param context  The {@link HttpContext} associated with the request execution.
     * @return {@code true} if the request should be redirected, {@code false} otherwise.
     * @throws ProtocolException if an error occurs during the protocol execution.
     */
    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        if (!ConfigAccessor.getConfigValue("followRedirects", Boolean.class)) {
            return false;
        }
        boolean isRedirect = super.isRedirected(request, response, context);
        if (isRedirect && response.getFirstHeader("Location") == null) {
            return false;
        }
        return isRedirect;
    }

    /**
     * Returns the {@link HttpUriRequest} representing the redirect that should be followed.
     *
     * <p>This method delegates the redirection handling to the superclass, which generates
     * the appropriate HTTP request for the redirect.
     *
     * @param request  The original {@link HttpRequest} that was sent.
     * @param response The {@link HttpResponse} received from the server.
     * @param context  The {@link HttpContext} associated with the request execution.
     * @return The {@link HttpUriRequest} to execute as the redirect.
     * @throws ProtocolException if an error occurs during the protocol execution.
     */
    @Override
    public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        return super.getRedirect(request, response, context);
    }
}
