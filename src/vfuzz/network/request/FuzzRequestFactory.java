package vfuzz.network.request;

import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.core.PayloadGenerator;
import vfuzz.except.RequestBuildingException;
import vfuzz.except.controlflow.PayloadGenerationFinishedException;
import vfuzz.except.controlflow.WordlistCompletedException;
import vfuzz.operations.RandomAgent;
import vfuzz.operations.Target;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class FuzzRequestFactory extends WebRequestFactory {

    PayloadGenerator payloadGenerator;

    public FuzzRequestFactory(Target target) {
        payloadGenerator = new PayloadGenerator(target.getWordlistReaders());
    }


    @Override
    public HttpRequestBase buildRequest() throws PayloadGenerationFinishedException {
        return null;
    }
}
