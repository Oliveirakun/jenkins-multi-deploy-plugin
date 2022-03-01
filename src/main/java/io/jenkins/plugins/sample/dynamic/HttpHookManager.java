package io.jenkins.plugins.sample.dynamic;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HttpHookManager {
    private final List<HttpHook> hooks;

    public HttpHookManager(List<HttpHook> hooks) {
        this.hooks = hooks;
    }

    public void execute() {
        hooks.forEach(hook -> {
            try {
                sendRequest(hook);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void sendRequest(HttpHook hook) throws IOException {
        StringEntity requestEntity = new StringEntity(hook.getPayload(), getContentType(hook));
        HttpUriRequest request = getMethod(hook);
        hook.getMapOfHeaders().forEach(request::addHeader);

        HttpClient client = HttpClients.createDefault();
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();

        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("Result for request to url: " + hook.getUrl());
        System.out.println("Status code: " + statusCode);
        System.out.println("Body content: " + IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8.name()));
        entity.getContent().close();
    }

    private ContentType getContentType(HttpHook hook) {
        ContentType typeToReturn = null;
        String contentType = hook.getMapOfHeaders().get("Content-Type");
        switch (contentType) {
            case "application/json":
                typeToReturn = ContentType.APPLICATION_JSON;
                break;
            case "application/xml":
                typeToReturn = ContentType.APPLICATION_XML;
                break;
            default:
                typeToReturn = ContentType.TEXT_PLAIN;
        }

        return typeToReturn;
    }

    private HttpUriRequest getMethod(HttpHook hook) {
        HttpUriRequest methodToReturn = null;
        switch (hook.getMethod()) {
            case "Post":
                methodToReturn = new HttpPost(hook.getUrl());
                break;
            case "Put":
                methodToReturn = new HttpPut(hook.getUrl());
                break;
            default:
                methodToReturn = new HttpGet(hook.getUrl());
        }

        return methodToReturn;
    }
}
