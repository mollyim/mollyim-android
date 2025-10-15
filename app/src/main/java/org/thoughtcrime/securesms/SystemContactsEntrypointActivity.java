package org.thoughtcrime.securesms;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;

public class SystemContactsEntrypointActivity extends PassphraseRequiredActivity {

  private static final String TAG = Log.tag(SystemContactsEntrypointActivity.class);

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    startActivity(getNextIntent(getIntent()));
    finish();
  }

  private Intent getNextIntent(Intent original) {
    final Uri uri = original.getData();

    final Intent nextIntent;

    if (uri != null && "content".equals(uri.getScheme())) {
      Recipient recipient = Recipient.external(getDestinationForSyncAdapter(uri));

      if (recipient != null) {
        long threadId = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);

        nextIntent = ConversationIntents.createBuilderSync(this, recipient.getId(), threadId)
                                        .withDraftText("")
                                        .build();
        return nextIntent;
      }
    }

    nextIntent = new Intent(this, NewConversationActivity.class);
    nextIntent.putExtra(Intent.EXTRA_TEXT, "");
    Toast.makeText(this, R.string.ConversationActivity_specify_recipient, Toast.LENGTH_LONG).show();
    return nextIntent;
  }

  private @NonNull String getDestinationForSyncAdapter(@NonNull Uri uri) {
    try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToNext()) {
        return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.Data.DATA1));
      }
    }
    return "";
  }
}
