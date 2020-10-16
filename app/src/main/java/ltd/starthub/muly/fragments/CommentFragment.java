package ltd.starthub.muly.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kaopiz.kprogresshud.KProgressHUD;
import com.stfalcon.chatkit.messages.MessageInput;

import java.util.List;

import jp.wasabeef.recyclerview.adapters.SlideInLeftAnimationAdapter;
import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.R;
import ltd.starthub.muly.activities.MainActivity;
import ltd.starthub.muly.common.DiffUtilCallback;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.CommentDataSource;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.Comment;
import ltd.starthub.muly.data.models.Wrappers;
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommentFragment extends Fragment {

    public static final String ARG_CLIP = "clip";
    public static final String TAG = "CommentFragment";

    private CommentFragmentViewModel mModel1;
    private MainActivity.MainActivityViewModel mModel2;
    private int mClip;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mClip = requireArguments().getInt(ARG_CLIP);
        CommentFragmentViewModel.Factory factory = new CommentFragmentViewModel.Factory(mClip);
        mModel1 = new ViewModelProvider(this, factory).get(CommentFragmentViewModel.class);
        mModel2 = new ViewModelProvider(requireActivity())
                .get(MainActivity.MainActivityViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_comments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.header_back)
                .setOnClickListener(v -> requireActivity()
                        .getSupportFragmentManager()
                        .popBackStack());
        TextView title = view.findViewById(R.id.header_title);
        title.setText(R.string.comments_label);
        view.findViewById(R.id.header_more).setVisibility(View.GONE);
        RecyclerView comments = view.findViewById(R.id.comments);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, true);
        lm.setStackFromEnd(true);
        comments.setLayoutManager(lm);
        CommentsAdapter adapter = new CommentsAdapter();
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int last = lm.findLastCompletelyVisibleItemPosition();
                if (last == -1 || positionStart >= adapter.getItemCount() - 1 && last == positionStart - 1) {
                    comments.scrollToPosition(positionStart);
                }
            }
        });
        comments.setAdapter(new SlideInLeftAnimationAdapter(adapter));
        OverScrollDecoratorHelper.setUpOverScroll(
                comments, OverScrollDecoratorHelper.ORIENTATION_VERTICAL);
        mModel1.comments.observe(getViewLifecycleOwner(), adapter::submitList);
        View empty = view.findViewById(R.id.empty);
        View loading = view.findViewById(R.id.loading);
        mModel1.state.observe(getViewLifecycleOwner(), state -> {
            List<?> list = mModel1.comments.getValue();
            if (state == LoadingState.LOADING) {
                empty.setVisibility(View.GONE);
            } else {
                empty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
            }
            loading.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE);
        });
        MessageInput input = view.findViewById(R.id.input);
        input.setInputListener(message -> {
            if (mModel2.isLoggedIn) {
                submitComment(message);
                return true;
            }
            Toast.makeText(requireContext(), R.string.login_required_message, Toast.LENGTH_SHORT).show();
            return false;
        });
    }

    private void confirmDeletion(int comment) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.confirmation_delete_comment)
                .setNegativeButton(R.string.cancel_button, (dialog, i) -> dialog.cancel())
                .setPositiveButton(R.string.yes_button, (dialog, i) -> {
                    dialog.dismiss();
                    deleteComment(comment);
                })
                .show();
    }

    private void deleteComment(int comment) {
        KProgressHUD progress = KProgressHUD.create(requireActivity())
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .setLabel(getString(R.string.progress_title))
                .setCancellable(false)
                .show();
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.commentsDelete(mClip, comment)
                .enqueue(new Callback<ResponseBody>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<ResponseBody> call,
                            @Nullable Response<ResponseBody> response
                    ) {
                        int code = response != null ? response.code() : -1;
                        Log.v(TAG, "Deleting comment returned " + code + '.');
                        if (code == 200) {
                            CommentDataSource source = mModel1.factory.source.getValue();
                            if (source != null) {
                                source.invalidate();
                            }
                        }
                        progress.dismiss();
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<ResponseBody> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Failed to delete selected comment.", t);
                        progress.dismiss();
                    }
                });
    }

    public static CommentFragment newInstance(int clip) {
        CommentFragment fragment = new CommentFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_CLIP, clip);
        fragment.setArguments(arguments);
        return fragment;
    }

    @SuppressLint("SetTextI18n")
    private void prepareReply(Comment comment) {
        String prefill = "@" + comment.user.username + " ";
        //noinspection ConstantConditions
        MessageInput input = getView().findViewById(R.id.input);
        EditText editor = input.getInputEditText();
        editor.setText(prefill);
        editor.setSelection(prefill.length());
        editor.requestFocus();
        InputMethodManager imm =
                ContextCompat.getSystemService(requireContext(), InputMethodManager.class);
        //noinspection ConstantConditions
        imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
    }

    private void reportComment(int comment) {
        ((MainActivity)requireActivity()).reportSubject("comment", comment);
    }

    private void showProfile(int user) {
        ((MainActivity)requireActivity()).showProfilePage(user);
    }

    private void submitComment(CharSequence text) {
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.commentsCreate(mClip, text.toString())
                .enqueue(new Callback<Wrappers.Single<Comment>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Single<Comment>> call,
                            @Nullable Response<Wrappers.Single<Comment>> response
                    ) {
                        int code = response != null ? response.code() : -1;
                        Log.v(TAG, "Submitting comment returned " + code + '.');
                        if (response != null && response.isSuccessful()) {
                            CommentDataSource source = mModel1.factory.source.getValue();
                            if (source != null) {
                                source.invalidate();
                            }
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Single<Comment>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Failed to submit comment on clip.", t);
                    }
                });
    }

    private class CommentsAdapter extends PagedListAdapter<Comment, CommentViewHolder> {

        public CommentsAdapter() {
            super(new DiffUtilCallback<>(i -> i.id));
        }

        @NonNull
        @Override
        public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View root = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_comment, parent, false);
            return new CommentViewHolder(root);
        }

        @Override
        @SuppressLint("SetTextI18n")
        public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
            Comment comment = getItem(position);
            //noinspection ConstantConditions
            if (TextUtils.isEmpty(comment.user.photo)) {
                holder.photo.setActualImageResource(R.drawable.photo_placeholder);
            } else {
                holder.photo.setImageURI(comment.user.photo);
            }
            holder.username.setText('@' + comment.user.username);
            holder.verified.setVisibility(comment.user.verified ? View.VISIBLE : View.GONE);
            holder.text.setText(comment.text);
            holder.when.setText(
                    DateUtils.getRelativeTimeSpanString(
                            requireContext(), comment.createdAt.getTime(), true));
            holder.photo.setOnClickListener(v -> showProfile(comment.user.id));
            holder.username.setOnClickListener(v -> showProfile(comment.user.id));
            holder.reply.setOnClickListener(v -> {
                if (mModel2.isLoggedIn) {
                    prepareReply(comment);
                } else {
                    Toast.makeText(requireContext(), R.string.login_required_message, Toast.LENGTH_SHORT).show();
                }
            });
            holder.reply.setVisibility(comment.user.me ? View.GONE : View.VISIBLE);
            holder.report.setOnClickListener(v -> {
                if (mModel2.isLoggedIn) {
                    reportComment(comment.id);
                } else {
                    Toast.makeText(requireContext(), R.string.login_required_message, Toast.LENGTH_SHORT).show();
                }
            });
            holder.delete.setVisibility(comment.user.me ? View.GONE : View.VISIBLE);
            holder.delete.setOnClickListener(v -> {
                if (mModel2.isLoggedIn) {
                    confirmDeletion(comment.id);
                } else {
                    Toast.makeText(requireContext(), R.string.login_required_message, Toast.LENGTH_SHORT).show();
                }
            });
            holder.delete.setVisibility(comment.user.me ? View.VISIBLE : View.GONE);
        }
    }

    public static class CommentFragmentViewModel extends ViewModel {

        public CommentFragmentViewModel(int clip) {
            PagedList.Config config = new PagedList.Config.Builder()
                    .setPageSize(10)
                    .build();
            factory = new CommentDataSource.Factory(clip);
            state = Transformations.switchMap(factory.source, input -> input.state);
            comments = new LivePagedListBuilder<>(factory, config).build();
        }

        public final LiveData<PagedList<Comment>> comments;
        public final CommentDataSource.Factory factory;
        public final LiveData<LoadingState> state;

        private static class Factory implements ViewModelProvider.Factory {

            private final int mClip;

            public Factory(int clip) {
                mClip = clip;
            }

            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                //noinspection unchecked
                return (T)new CommentFragmentViewModel(mClip);
            }
        }
    }

    private static class CommentViewHolder extends RecyclerView.ViewHolder {

        public SimpleDraweeView photo;
        public TextView username;
        public ImageView verified;
        public TextView text;
        public TextView when;
        public View reply;
        public View report;
        public View delete;

        public CommentViewHolder(@NonNull View root) {
            super(root);
            username = root.findViewById(R.id.username);
            verified = root.findViewById(R.id.verified);
            photo = root.findViewById(R.id.photo);
            text = root.findViewById(R.id.text);
            when = root.findViewById(R.id.when);
            reply = root.findViewById(R.id.reply);
            report = root.findViewById(R.id.report);
            delete = root.findViewById(R.id.delete);
        }
    }
}
