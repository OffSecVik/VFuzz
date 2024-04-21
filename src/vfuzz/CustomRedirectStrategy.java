package vfuzz;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.protocol.HttpContext;

public class CustomRedirectStrategy extends DefaultRedirectStrategy {
    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        if (!ArgParse.getFollowRedirects()) {
            return false;
        }
        boolean isRedirect = super.isRedirected(request, response, context);
        if (isRedirect && response.getFirstHeader("Location") == null) {
            return false;
        }
        return isRedirect;
    }

    @Override
    public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        return super.getRedirect(request, response, context);
    }
}
