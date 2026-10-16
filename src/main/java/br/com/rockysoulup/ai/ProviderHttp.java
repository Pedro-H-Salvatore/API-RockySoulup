package br.com.rockysoulup.ai;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

@Component
public class ProviderHttp {
    public static final URI OPENAI=URI.create("https://api.openai.com/v1/responses");
    public static final URI HIVE=URI.create("https://api.thehive.ai/api/v3/hive/ai-generated-and-deepfake-content-detection");
    private static class ClientHolder {
        static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public byte[] post(URI destination,String key,byte[] body) {
        if(!Set.of(OPENAI,HIVE).contains(destination) || body.length>800000) throw new ProviderException("DESTINATION_BLOCKED");
        CompletableFuture<HttpResponse<byte[]>> call=null;
        try {
            var request=HttpRequest.newBuilder(destination).timeout(Duration.ofSeconds(20))
                    .header("Authorization","Bearer "+key).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            call=ClientHolder.CLIENT.sendAsync(request, info -> new BoundedSubscriber());
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
