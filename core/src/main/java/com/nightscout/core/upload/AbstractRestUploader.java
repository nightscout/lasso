package com.nightscout.core.upload;


import com.nightscout.core.preferences.NightscoutPreferences;

import net.tribe7.common.base.Joiner;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.AbstractHttpMessage;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;

import static net.tribe7.common.base.Preconditions.checkNotNull;

public abstract class AbstractRestUploader extends BaseUploader {
    private final URI uri;
    private HttpClient client;

    public AbstractRestUploader(NightscoutPreferences preferences, URI baseUri) {
        super(preferences);
        checkNotNull(baseUri);
        this.uri = baseUri;
        this.identifier = uri.getHost();
    }

    protected void setExtraHeaders(AbstractHttpMessage httpMessage) {
    }

    public URI getUri() {
        return uri;
    }

    public HttpClient getClient() {
        if (client != null) {
            return client;
        }
        client = new DefaultHttpClient();
        return client;
    }

    public void setClient(HttpClient client) {
        this.client = client;
    }

    protected boolean doPost(String endpoint, JSONObject jsonObject) throws IOException {
        HttpPost httpPost = new HttpPost(Joiner.on('/').join(uri.toString(), endpoint));
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.addHeader("Accept", "application/json");
        setExtraHeaders(httpPost);
        httpPost.setEntity(new StringEntity(jsonObject.toString()));
        HttpResponse response = getClient().execute(httpPost);
        log.error("JSON in doPost: {}", jsonObject);
        log.error("Response code: {}", response.getStatusLine().getStatusCode());
        int statusCodeFamily = response.getStatusLine().getStatusCode() / 100;
        response.getEntity().consumeContent();
        return statusCodeFamily == 2;
    }
}
