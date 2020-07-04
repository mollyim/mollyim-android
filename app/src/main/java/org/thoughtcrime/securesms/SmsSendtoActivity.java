package org.thoughtcrime.securesms;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.conversation.ConversationActivity;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;

public class SmsSendtoActivity extends PassphraseRequiredActivity {

  private static final String TAG = SmsSendtoActivity.class.getSimpleName();

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
      Recipient recipient = Recipient.external(this, getDestinationForSyncAdapter(uri));
      long threadId = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient);

      nextIntent = new Intent(this, ConversationActivity.class);
      nextIntent.putExtra(ConversationActivity.TEXT_EXTRA, "");
      nextIntent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
      nextIntent.putExtra(ConversationActivity.RECIPIENT_EXTRA, recipient.getId());
    } else {
      nextIntent = new Intent(this, NewConversationActivity.class);
    }
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
