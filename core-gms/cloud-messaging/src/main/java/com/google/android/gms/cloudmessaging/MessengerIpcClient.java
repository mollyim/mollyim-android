package com.google.android.gms.cloudmessaging;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.stats.ConnectionTracker;
import com.google.android.gms.common.util.concurrent.NamedThreadFactory;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MessengerIpcClient {

  private static final String TAG = "MessengerIpcClient";

  private static final String ACTION_REGISTER = "com.google.android.c2dm.intent.REGISTER";

  private static final long TIMEOUT_SECONDS = 30L;

  @Nullable
  private static volatile MessengerIpcClient instance;

  private final Context                  context;
  private final ScheduledExecutorService executor;
  private final AtomicInteger            nextRequestId;

  private Connection connection;

  private MessengerIpcClient(Context context, ScheduledExecutorService executor) {
    this.context       = context.getApplicationContext();
    this.executor      = executor;
    this.nextRequestId = new AtomicInteger(1);
    this.connection    = new Connection();
  }

  public static MessengerIpcClient getInstance(Context context) {
    if (instance == null) {
      synchronized (MessengerIpcClient.class) {
        if (instance == null) {
          instance = new MessengerIpcClient(
              context,
              Executors.unconfigurableScheduledExecutorService(
                  Executors.newScheduledThreadPool(1, new NamedThreadFactory(TAG))
              )
          );
        }
      }
    }
    return instance;
  }

  public Task<Bundle> sendRequest(@GmsOpCode.Code int what, Bundle data) {
    return enqueue(new TwoWayRequest(nextRequestId(), what, data));
  }

  public Task<Void> sendOneWay(@GmsOpCode.Code int what, Bundle data) {
    return enqueue(new OneWayRequest(nextRequestId(), what, data));
  }

  private <T> Task<T> enqueue(Request<T> request) {
    synchronized (this) {
      if (!connection.enqueueRequest(request)) {
        connection = new Connection();
        connection.enqueueRequest(request);
      }
    }
    return request.completion.getTask();
  }

  private int nextRequestId() {
    return nextRequestId.getAndIncrement();
  }

  private class Connection implements ServiceConnection {

    private enum State { IDLE, BINDING, BOUND, UNBINDING, DEAD }

    private State state = State.IDLE;

    private final Messenger replyMessenger;
    private       Messenger serviceMessenger;

    private final ConnectionTracker connectionTracker = ConnectionTracker.getInstance();

    private final Queue<Request<?>>       pendingRequests = new ArrayDeque<>();
    private final SparseArray<Request<?>> activeRequests  = new SparseArray<>();

    private Connection() {
      replyMessenger = new Messenger(
          new Handler(Looper.getMainLooper(), this::handleReply)
      );
    }

    synchronized boolean enqueueRequest(Request<?> request) {
      switch (state) {
        case IDLE:
          state = State.BINDING;
          pendingRequests.add(request);
          bindService();
          break;
        case BINDING:
          pendingRequests.add(request);
          break;
        case BOUND:
          pendingRequests.add(request);
          sendPendingRequests();
          break;
        default:
          return false;
      }
      return true;
    }

    private void bindService() {
      Log.v(TAG, "Starting bind to GmsCore");

      final Intent intent = new Intent(ACTION_REGISTER);
      intent.setPackage(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE);

      try {
        boolean bound = connectionTracker.bindService(context, intent, this, Context.BIND_AUTO_CREATE);
        if (bound) {
          executor.schedule(this::onBindTimeout, TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } else {
          handleDisconnection("Unable to bind to service");
        }
      } catch (SecurityException e) {
        handleDisconnection("Unable to bind to service", e);
      }
    }

    @MainThread
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
      Log.v(TAG, "Service connected");
      executor.execute(() -> handleBound(binder));
    }

    @MainThread
    @Override
    public void onServiceDisconnected(ComponentName name) {
      Log.v(TAG, "Service disconnected");
      executor.execute(() -> handleDisconnection("Service disconnected"));
    }

    private synchronized void onBindTimeout() {
      if (state == State.BINDING) {
        handleDisconnection("Timed out while binding");
      }
    }

    private synchronized void handleBound(IBinder binder) {
      try {
        if (!"android.os.IMessenger".equals(binder.getInterfaceDescriptor())) {
          throw new RemoteException("Invalid interface descriptor");
        }
        serviceMessenger = new Messenger(binder);
        state = State.BOUND;
        sendPendingRequests();
      } catch (RemoteException e) {
        handleDisconnection("Service connection failed", e);
      }
    }

    private synchronized void handleDisconnection(@Nullable String reason) {
      handleDisconnection(reason, null);
    }

    private synchronized void handleDisconnection(@Nullable String reason, @Nullable Throwable cause) {
      switch (state) {
        case IDLE:
          throw new IllegalStateException();
        case BINDING:
        case BOUND:
          state = State.DEAD;
          connectionTracker.unbindService(context, this);
          failAndClearAll(reason, cause);
          break;
        case UNBINDING:
          state = State.DEAD;
          break;
      }
    }

    private void failAndClearAll(@Nullable String reason, @Nullable Throwable cause) {
      final CloudMessagingException e = new CloudMessagingException(reason, cause);

      for (Request<?> pendingRequest : pendingRequests) {
        pendingRequest.fail(e);
      }
      pendingRequests.clear();

      for (int i = 0; i < activeRequests.size(); i++) {
        activeRequests.valueAt(i).fail(e);
      }
      activeRequests.clear();
    }

    private void sendPendingRequests() {
      executor.execute(() -> {
        while (true) {
          Request<?> request;

          synchronized (this) {
            if (state != State.BOUND) {
              return;
            }
            request = pendingRequests.poll();
            if (request == null) {
              maybeUnbind();
              return;
            }
            activeRequests.put(request.id, request);
            executor.schedule(() -> timeoutRequest(request.id), TIMEOUT_SECONDS, TimeUnit.SECONDS);
          }

          Message msg = Message.obtain();
          msg.what    = request.what;
          msg.arg1    = request.id;
          msg.replyTo = replyMessenger;

          Bundle data = new Bundle();
          data.putBoolean("oneWay", request.isOneWay());
          data.putString("pkg", context.getPackageName());
          data.putBundle("data", request.payload);
          msg.setData(data);

          try {
            serviceMessenger.send(msg);
          } catch (RemoteException e) {
            handleDisconnection(e.getMessage());
          }
        }
      });
    }

    private synchronized void timeoutRequest(int requestId) {
      Request<?> r = activeRequests.get(requestId);
      if (r != null) {
        Log.w(TAG, "Timing out request: " + requestId);
        activeRequests.remove(requestId);
        r.fail(new CloudMessagingException("Timed out waiting for response"));
        maybeUnbind();
      }
    }

    private boolean handleReply(Message msg) {
      final Request<?> request;

      synchronized (this) {
        int requestId = msg.arg1;
        request = activeRequests.get(requestId);
        if (request == null) {
          Log.w(TAG, "Unknown request response: " + requestId);
          return true;
        }
        activeRequests.remove(requestId);
        maybeUnbind();
      }

      Bundle data = msg.getData();
      if (data.getBoolean("unsupported", false)) {
        request.fail(new CloudMessagingException("Not supported by GmsCore"));
      } else {
        request.onResponse(data);
      }
      return true;
    }

    private synchronized void maybeUnbind() {
      if (state == State.BOUND && activeRequests.size() == 0 && pendingRequests.isEmpty()) {
        Log.v(TAG, "Finished handling requests, unbinding");
        state = State.UNBINDING;
        connectionTracker.unbindService(context, this);
      }
    }
  }

  abstract static class Request<T> {

    final int    id;
    final int    what;
    final Bundle payload;

    final TaskCompletionSource<T> completion = new TaskCompletionSource<>();

    Request(int id, int what, Bundle payload) {
      this.id      = id;
      this.what    = what;
      this.payload = payload;
    }

    final void fail(CloudMessagingException e) {
      completion.setException(e);
    }

    final void complete(T result) {
      completion.setResult(result);
    }

    abstract boolean isOneWay();

    abstract void onResponse(Bundle response);

    @Override
    @NonNull
    public String toString() {
      return "Request { what=" + what + " id=" + id + " oneWay=" + isOneWay() + "}";
    }
  }

  static final class OneWayRequest extends Request<Void> {
    OneWayRequest(int id, int what, Bundle payload) {
      super(id, what, payload);
    }

    @Override
    boolean isOneWay() {
      return true;
    }

    @Override
    void onResponse(Bundle response) {
      if (response.getBoolean("ack", false)) {
        complete(null);
      } else {
        fail(new CloudMessagingException("Invalid response to one way request"));
      }
    }
  }

  static final class TwoWayRequest extends Request<Bundle> {
    TwoWayRequest(int id, int what, Bundle payload) {
      super(id, what, payload);
    }

    @Override
    boolean isOneWay() {
      return false;
    }

    @Override
    void onResponse(Bundle response) {
      Bundle data = response.getBundle("data");
      if (data == null) {
        data = Bundle.EMPTY;
      }
      complete(data);
    }
  }
}
