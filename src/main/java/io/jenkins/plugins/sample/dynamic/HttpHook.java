package io.jenkins.plugins.sample.dynamic;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class HttpHook {
    private String method;
    private String payload;
    private String headers;
    private String url;

    @DataBoundConstructor
    public HttpHook(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    @DataBoundSetter
    public void setMethod(String method) {
        this.method = method;
    }

    public String getPayload() {
        return payload;
    }

    @DataBoundSetter
    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getHeaders() {
        return headers;
    }

    @DataBoundSetter
    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public String getUrl() {
        return url;
    }

    @DataBoundSetter
    public void setUrl(String url) {
        this.url = url;
    }
}
