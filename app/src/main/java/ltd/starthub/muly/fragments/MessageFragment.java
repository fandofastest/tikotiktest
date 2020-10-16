package ltd.starthub.muly.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.view.SimpleDraweeView;
import com.stfalcon.chatkit.messages.MessageInput;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import jp.wasabeef.recyclerview.adapters.SlideInLeftAnimationAdapter;
import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.R;
import ltd.starthub.muly.activities.MainActivity;
import ltd.starthub.muly.common.DiffUtilCallback;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.MessageDataSource;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.Message;
import ltd.starthub.muly.data.models.Wrappers;
import ltd.starthub.muly.events.MessageEvent;
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessageFragment extends Fragment {

    private static final String ARG_THREAD = "thread";
    private static final String ARG_USERNAME = "username";
    private static final String TAG = "MessageFragment";

    private int mThread;
    private String mUsername;

    private MessageFragmentViewModel mModel1;
    private MainActivity.MainActivityViewModel mModel2;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mThread = requireArguments().getInt(ARG_THREAD, 0);
        mUsername = requireArguments().getString(ARG_USERNAME);
        MessageFragmentViewModel.Factory factory =
                new MessageFragmentViewModel.Factory(mThread);
        mModel1 = new ViewModelProvider(this, factory).get(MessageFragmentViewModel.class);
        mModel2 = new ViewModelProvider(requireActivity())
                .get(MainActivity.MainActivityViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_message, container, false);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        if (event.thread == mThread) {
            MessageDataSource source = mModel1.factory.source.getValue();
            if (source != null) {
                source.invalidate();
            }
        }
        mModel2.areThreadsInvalid = true;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View back = view.findViewById(R.id.header_back);
        back.setOnClickListener(v -> requireActivity()
                .getSupportFragmentManager()
                .popBackStack());
        TextView title = view.findViewById(R.id.header_title);
        title.setText("@" + mUsername);
        view.findViewById(R.id.header_more).setVisibility(View.GONE);
        RecyclerView messages = view.findViewById(R.id.messages);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, true);
        lm.setStackFromEnd(true);
        messages.setLayoutManager(lm);
        MessageAdapter adapter = new MessageAdapter();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int last = lm.findLastCompletelyVisibleItemPosition();
                if (last == -1 || positionStart >= adapter.getItemCount() - 1 && last == positionStart - 1) {
                    messages.scrollToPosition(positionStart);
                }
            }
        });
        messages.setAdapter(new SlideInLeftAnimationAdapter(adapter));
        OverScrollDecoratorHelper.setUpOverScroll(
                messages, OverScrollDecoratorHelper.ORIENTATION_VERTICAL);
        View empty = view.findViewById(R.id.empty);
        View loading = view.findViewById(R.id.loading);
        mModel1.messages.observe(getViewLifecycleOwner(), adapter::submitList);
        mModel1.state.observe(getViewLifecycleOwner(), state -> {
            List<?> list = mModel1.messages.getValue();
            if (state == LoadingState.LOADING) {
                empty.setVisibility(View.GONE);
            } else {
                empty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
            }
            loading.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE);
        });
        MessageInput input = view.findViewById(R.id.input);
        input.setInputListener(message -> {
            submitMessage(message);
            return true;
        });
    }

    public static MessageFragment newInstance(int thread, String username) {
        MessageFragment fragment = new MessageFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_THREAD, thread);
        arguments.putString(ARG_USERNAME, username);
        fragment.setArguments(arguments);
        return fragment;
    }

    private void showProfile(int user) {
        ((MainActivity)requireActivity()).showProfilePage(user);
    }

    private void submitMessage(CharSequence text) {
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.messagesCreate(mThread, text.toString())
                .enqueue(new Callback<Wrappers.Single<Message>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Single<Message>> call,
                            @Nullable Response<Wrappers.Single<Message>> response
                    ) {
                        int code = response != null ? response.code() : -1;
                        Log.v(TAG, "Submitting message returned " + code + '.');
                        if (response != null && response.isSuccessful()) {
                            MessageDataSource source = mModel1.factory.source.getValue();
                            if (source != null) {
                                source.invalidate();
                            }
                            mModel2.areThreadsInvalid = true;
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Single<Message>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Failed to send message in thread.", t);
                    }
                });
    }

    private class MessageAdapter extends PagedListAdapter<Message, MessageViewHolder> {

        private static final int TYPE_INBOX = 100;
        private static final int TYPE_OUTBOX = 101;

        protected MessageAdapter() {
            super(new DiffUtilCallback<>(i -> i.id));
        }

        @Override
        public int getItemViewType(int position) {
            Message message = getItem(position);
            //noinspection ConstantConditions
            return message.user.me ? TYPE_OUTBOX : TYPE_INBOX;
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View view = LayoutInflater.from(requireContext())
                    .inflate(type == TYPE_INBOX ? R.layout.item_message_in : R.layout.item_message_out, parent, false);
            return new MessageViewHolder(view);
        }

        @Override
        @SuppressLint("SetTextI18n")
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            Message message = getItem(position);
            //noinspection ConstantConditions
            if (!message.user.me) {
                if (TextUtils.isEmpty(message.user.photo)) {
                    holder.photo.setActualImageResource(R.drawable.photo_placeholder);
                } else {
                    holder.photo.setImageURI(message.user.photo);
                }
                holder.photo.setOnClickListener(v -> showProfile(message.user.id));
                holder.username.setText('@' + message.user.username);
                holder.username.setOnClickListener(v -> showProfile(message.user.id));
                holder.verified.setVisibility(message.user.verified ? View.VISIBLE : View.GONE);
            }
            holder.text.setText(message.body);
            holder.when.setText(
                    DateUtils.getRelativeTimeSpanString(
                            requireContext(), message.createdAt.getTime(), true));
        }
    }

    public static class MessageFragmentViewModel extends ViewModel {

        public MessageFragmentViewModel(int thread) {
            PagedList.Config config = new PagedList.Config.Builder()
                    .setPageSize(10)
                    .build();
            factory = new MessageDataSource.Factory(thread);
            state = Transformations.switchMap(factory.source, input -> input.state);
            messages = new LivePagedListBuilder<>(factory, config).build();
        }

        public final LiveData<PagedList<Message>> messages;
        public final MessageDataSource.Factory factory;
        public final LiveData<LoadingState> state;

        private static class Factory implements ViewModelProvider.Factory {

            private final int mThread;

            public Factory(int thread) {
                mThread = thread;
            }

            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                //noinspection unchecked
                return (T)new MessageFragment.MessageFragmentViewModel(mThread);
            }
        }
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {

        public SimpleDraweeView photo;
        public TextView username;
        public ImageView verified;
        public TextView text;
        public TextView when;

        public MessageViewHolder(@NonNull View root) {
            super(root);
            username = root.findViewById(R.id.username);
            verified = root.findViewById(R.id.verified);
            photo = root.findViewById(R.id.photo);
            text = root.findViewById(R.id.text);
            when = root.findViewById(R.id.when);
        }
    }
}
