package br.com.rockysoulup.ai;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import javax.net.ssl.*;
import java.security.KeyStore;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

@Component
public class ProviderHttp {
    public static final URI GEMINI=URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent");
    private final boolean windowsTrustStore;
    public ProviderHttp() { this(false); }
    @Autowired
    public ProviderHttp(@Value("${app.ai.windows-trust-store:false}") boolean windowsTrustStore) {
        this.windowsTrustStore=windowsTrustStore;
    }
    private static class ClientHolder {
        static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private static class WindowsClientHolder {
        static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).sslContext(windowsSslContext()).build();
    }
    static SSLContext windowsSslContext() {
        try {
            KeyStore store=KeyStore.getInstance("Windows-ROOT");store.load(null,null);
            var managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());managers.init(store);
            var context=SSLContext.getInstance("TLS");context.init(null,managers.getTrustManagers(),null);
            return context;
        } catch(Exception e) { throw new IllegalStateException("Repositorio TLS do Windows indisponivel."); }
    }
    public byte[] post(URI destination,String key,byte[] body) {
        if(!GEMINI.equals(destination) || body.length>800000) throw new ProviderException("DESTINATION_BLOCKED");
        CompletableFuture<HttpResponse<byte[]>> call=null;
        try {
            var request=HttpRequest.newBuilder(destination).timeout(Duration.ofSeconds(20))
                    .header("x-goog-api-key",key).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var client=windowsTrustStore?WindowsClientHolder.CLIENT:ClientHolder.CLIENT;
            call=client.sendAsync(request, info -> new BoundedSubscriber());
            var response=call.get(21,TimeUnit.SECONDS);
            if(response.statusCode()!=200) throw new ProviderException("HTTP_FAILURE");
            return response.body();
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new ProviderException("INTERRUPTED"); }
        catch(ProviderException e) { throw e; }
        catch(Exception e) { throw new ProviderException("TIMEOUT_OR_NETWORK"); }
        finally { if(call!=null && !call.isDone()) call.cancel(true); }
    }
    // Cancel as soon as the bounded JSON response is exceeded; never buffer arbitrary provider bodies.
    static class BoundedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int size;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription s) { subscription=s; delegate.onSubscribe(s); }
        public void onNext(List<ByteBuffer> buffers) {
            for(var b:buffers) {
                size+=b.remaining();
                if(size>65536) { subscription.cancel(); delegate.onError(new ProviderException("RESPONSE_TOO_LARGE")); return; }
            }
            delegate.onNext(buffers);
        }
        public void onError(Throwable t) { delegate.onError(t); }
        public void onComplete() { delegate.onComplete(); }
    }
}
