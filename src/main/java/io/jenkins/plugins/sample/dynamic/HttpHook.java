package io.jenkins.plugins.sample.dynamic;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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

    public Map<String,String> getMapOfHeaders() {
        Map<String,String> mapHeaders = new HashMap<>();
        Arrays.stream(headers.split("\n")).forEach(env -> {
            if (env.isEmpty())
                return;

            String[] param = env.split(":", 2);
            String key = param[0];
            String value = param[1];
            mapHeaders.put(key, value);
        });

        return mapHeaders;
    }
}
