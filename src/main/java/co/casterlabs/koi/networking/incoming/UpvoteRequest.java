package co.casterlabs.koi.networking.incoming;

import com.google.gson.annotations.SerializedName;

import lombok.Getter;
import xyz.e3ndr.eventapi.events.AbstractEvent;

@Getter
public class UpvoteRequest extends AbstractEvent<RequestType> {

    public UpvoteRequest() {
        super(RequestType.UPVOTE);
    }

    private String nonce;

    @SerializedName("message_id")
    private String messageId;

}
