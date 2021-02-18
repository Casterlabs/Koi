package co.casterlabs.koi.user;

import co.casterlabs.koi.client.Client;
import co.casterlabs.koi.client.ClientAuthProvider;
import lombok.NonNull;

public interface UserProvider {

    public void hookWithAuth(@NonNull Client client, @NonNull ClientAuthProvider auth) throws IdentifierException;

    public void hook(@NonNull Client client, @NonNull String username) throws IdentifierException;

    default void upvote(@NonNull Client client, @NonNull String id, @NonNull ClientAuthProvider auth) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    default void chat(@NonNull Client client, @NonNull String message, @NonNull ClientAuthProvider auth) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /*
    default void deleteMessage(@NonNull Client client, @NonNull String messageId, @NonNull KoiAuthProvider auth) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }
    */ // Soon.

}
