package com.ipinfo.service;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.ipinfo.model.IpData;
import com.ipinfo.model.ProxyData;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import lombok.extern.java.Log;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Log
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IpLookupService {

    private static final String IPAPI_URL =
            "http://ip-api.com/json/%s?fields=status,message,country,countryCode,regionName,city,zip,lat,lon,timezone,isp,org,as,query";

    private static final String PROXYCHECK_URL =
            "https://proxycheck.io/v2/%s?vpn=1&asn=1&risk=1&port=1&seen=1&days=7";

    HttpClient httpClient;
    Gson gson;
    ExecutorService executor;

    public IpLookupService() {
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "ipinfo-lookup");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(executor)
                .build();
        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .create();
    }

    public void shutdown() {
        executor.shutdown();
    }

    public CompletableFuture<IpData> lookup(@NonNull String ip) {
        return httpClient.sendAsync(buildRequest(IPAPI_URL, ip), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> gson.fromJson(response.body(), IpData.class));
    }

    public CompletableFuture<ProxyData> lookupProxy(@NonNull String ip) {
        return httpClient.sendAsync(buildRequest(PROXYCHECK_URL, ip), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseProxyCheck(response.body(), ip));
    }

    private ProxyData parseProxyCheck(String body, String ip) {
        try {
            JsonObject root = gson.fromJson(body, JsonObject.class);
            if (!root.has(ip)) return ProxyData.builder().build();

            JsonObject entry = root.getAsJsonObject(ip);

            String proxy    = entry.has("proxy")    ? entry.get("proxy").getAsString()    : "no";
            String type     = entry.has("type")     ? entry.get("type").getAsString()     : "";
            String provider = entry.has("provider") ? entry.get("provider").getAsString() : "";

            return ProxyData.builder()
                    .proxy("yes".equalsIgnoreCase(proxy))
                    .vpn("VPN".equalsIgnoreCase(type))
                    .tor("TOR".equalsIgnoreCase(type))
                    .hosting("Hosting".equalsIgnoreCase(type) || "Server".equalsIgnoreCase(type))
                    .type(type)
                    .provider(provider)
                    .build();
        } catch (Exception ex) {
            log.warning("Failed to parse proxycheck.io response for IP " + ip + ": " + ex.getMessage());
            return ProxyData.builder().build();
        }
    }

    private HttpRequest buildRequest(String urlTemplate, String ip) {
        return HttpRequest.newBuilder()
                .uri(URI.create(String.format(urlTemplate, ip)))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
    }
}
