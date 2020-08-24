package co.casterlabs.koi.events;

import co.casterlabs.koi.user.SerializedUser;
import co.casterlabs.koi.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class SubscriptionEvent extends Event {
    private SerializedUser subscriber;
    private User streamer;
    private int months;

    @Override
    public EventType getType() {
        return EventType.SUBSCRIPTION;
    }

}
