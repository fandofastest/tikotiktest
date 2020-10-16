package ltd.starthub.muly.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.kaopiz.kprogresshud.KProgressHUD;

import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.R;
import ltd.starthub.muly.activities.MainActivity;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.ClipDataSource;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.Thread;
import ltd.starthub.muly.data.models.User;
import ltd.starthub.muly.data.models.Wrappers;
import ltd.starthub.muly.utils.TextFormatUtil;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private static final String ARG_USER = "user";
    private static final String TAG = "ProfileFragment";

    private ProfileFragmentViewModel mModel1;
    private MainActivity.MainActivityViewModel mModel2;
    private Integer mUser;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUser = requireArguments().getInt(ARG_USER, 0);
        if (mUser <= 0) {
            mUser = null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        mModel1 = new ViewModelProvider(this).get(ProfileFragmentViewModel.class);
        mModel2 = new ViewModelProvider(requireActivity()).get(MainActivity.MainActivityViewModel.class);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        //noinspection ConstantConditions
        ViewPager2 pager = getView().findViewById(R.id.pager);
        LoadingState state = mModel1.state.getValue();
        User user = mModel1.user.getValue();
        if ((mModel2.isProfileInvalid || user == null) && state != LoadingState.LOADING) {
            loadUser();
        } else if (user != null && pager.getAdapter() == null) {
            showClipsGrid(user);
        }
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View back = view.findViewById(R.id.header_back);
        back.setOnClickListener(v -> requireActivity()
                .getSupportFragmentManager()
                .popBackStack());
        back.setVisibility(mUser == null ? View.GONE : View.VISIBLE);
        TextView title = view.findViewById(R.id.header_title);
        title.setText(R.string.profile_label);
        View more = view.findViewById(R.id.header_more);
        more.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenuInflater().inflate(R.menu.profile_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case R.id.edit:
                        showEditor();
                        break;
                    case R.id.logout:
                        logoutUser();
                        break;
                }

                return true;
            });
            popup.show();

        });
        more.setVisibility(mUser != null ? View.GONE : View.VISIBLE);
        View actions = view.findViewById(R.id.actions);
        actions.setVisibility(mUser == null ? View.GONE : View.VISIBLE);
        MaterialButton follow = view.findViewById(R.id.follow);
        follow.setOnClickListener(v -> {
            if (mModel2.isLoggedIn) {
                followUnfollowUser();
            } else {
                Toast.makeText(requireContext(), R.string.login_required_message, Toast.LENGTH_SHORT).show();
            }
        });
        View chat = view.findViewById(R.id.chat);
        chat.setOnClickListener(v -> {
            if (mModel2.isLoggedIn) {
                startChat();
            } else {
                Toast.makeText(requireContext(), R.string.login_required_message, Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.followers_count)
                .setOnClickListener(v -> showFollowerFollowing(false));
        view.findViewById(R.id.followed_count)
                .setOnClickListener(v -> showFollowerFollowing(true));
        View loading = view.findViewById(R.id.loading);
        mModel1.state.observe(getViewLifecycleOwner(), state -> {
            if (state == LoadingState.ERROR) {
                Toast.makeText(requireContext(), R.string.error_internet, Toast.LENGTH_SHORT).show();
            }

            loading.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE);
        });
        mModel1.user.observe(getViewLifecycleOwner(), user -> {
            if (user == null) {
                return;
            }
            SimpleDraweeView photo = view.findViewById(R.id.photo);
            if (TextUtils.isEmpty(user.photo)) {
                photo.setActualImageResource(R.drawable.photo_placeholder);
            } else {
                photo.setImageURI(user.photo);
            }
            TextView name = view.findViewById(R.id.name);
            name.setText(user.name);
            TextView username = view.findViewById(R.id.username);
            username.setText('@' + user.username);
            view.findViewById(R.id.verified)
                    .setVisibility(user.verified ? View.VISIBLE : View.GONE);
            TextView bio = view.findViewById(R.id.bio);
            bio.setText(user.bio);
            bio.setVisibility(TextUtils.isEmpty(user.bio) ? View.GONE : View.VISIBLE);
            TextView views = view.findViewById(R.id.views);
            views.setText(TextFormatUtil.toShortNumber(user.viewsCount));
            TextView likes = view.findViewById(R.id.likes);
            likes.setText(TextFormatUtil.toShortNumber(user.likesCount));
            TextView followers = view.findViewById(R.id.followers);
            followers.setText(TextFormatUtil.toShortNumber(user.followersCount));
            TextView followed = view.findViewById(R.id.followed);
            followed.setText(TextFormatUtil.toShortNumber(user.followedCount));
            actions.setVisibility(user.me ? View.GONE : View.VISIBLE);
            follow.setIconResource(user.followed ? R.drawable.ic_unfollow : R.drawable.ic_follow);
            follow.setText(user.followed ? R.string.unfollow_label : R.string.follow_label);
            showClipsGrid(user);
        });
    }

    private void followUnfollowUser() {
        User user = mModel1.user.getValue();
        if (user == null) {
            return;
        }
        REST rest = MainApplication.getContainer().get(REST.class);
        Call<ResponseBody> call;
        if (user.followed) {
            call = rest.followersUnfollow(user.id);
        } else {
            call = rest.followersFollow(user.id);
        }
        call.enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(
                    @Nullable Call<ResponseBody> call,
                    @Nullable Response<ResponseBody> response) {
                int code = response != null ? response.code() : -1;
                Log.v(TAG, "Updating follow/unfollow returned " + code + '.');
            }

            @Override
            public void onFailure(
                    @Nullable Call<ResponseBody> call,
                    @Nullable Throwable t) {
                Log.e(TAG, "Failed to update follow/unfollow user.", t);
            }
        });
        if (user.followed) {
            user.followersCount--;
        } else {
            user.followersCount++;
        }
        user.followed = !user.followed;
        mModel1.user.postValue(user);
    }

    private void loadUser() {
        mModel1.state.postValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        Call<Wrappers.Single<User>> call;
        if (mUser == null) {
            call = rest.profileShow();
        } else {
            call = rest.usersShow(mUser);
        }
        call.enqueue(new Callback<Wrappers.Single<User>>() {

            @Override
            public void onResponse(
                    @Nullable Call<Wrappers.Single<User>> call,
                    @Nullable Response<Wrappers.Single<User>> response
            ) {
                int code = response != null ? response.code() : -1;
                Log.v(TAG, "Fetching user profile returned " + code + '.');
                if (code == 200) {
                    //noinspection ConstantConditions
                    mModel1.user.postValue(response.body().data);
                    mModel2.isProfileInvalid = false;
                    mModel1.state.postValue(LoadingState.LOADED);
                } else {
                    mModel1.state.postValue(LoadingState.ERROR);
                }
            }

            @Override
            public void onFailure(
                    @Nullable Call<Wrappers.Single<User>> call,
                    @Nullable Throwable t
            ) {
                Log.e(TAG, "Failed when trying to retrieve profile.", t);
                mModel1.state.postValue(LoadingState.ERROR);
            }
        });
    }

    private void logoutUser() {
        ((MainActivity)requireActivity()).logout();
    }

    public static ProfileFragment newInstance(@Nullable Integer user) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle arguments = new Bundle();
        if (user != null) {
            arguments.putInt(ARG_USER, user);
        }

        fragment.setArguments(arguments);
        return fragment;
    }

    private void showEditor() {
        User user = mModel1.user.getValue();
        if (user != null) {
            ((MainActivity)requireActivity()).showProfileEditor();
        }
    }

    private void showFollowerFollowing(boolean following) {
        User user = mModel1.user.getValue();
        if (user != null) {
            ((MainActivity)requireActivity()).showFollowerFollowing(user.id, following);
        }
    }

    private void showClipsGrid(User user) {
        //noinspection ConstantConditions
        TabLayout tabs = getView().findViewById(R.id.tabs);
        ViewPager2 pager = getView().findViewById(R.id.pager);
        pager.setAdapter(new ProfilePagerAdapter(user, this));
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_video_library_24));
                    break;
                case 1:
                    tab.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_favorite_24));
                    break;
                case 2:
                    tab.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_save_24));
                    break;
            }
        }).attach();
    }

    private void startChat() {
        User user = mModel1.user.getValue();
        if (user == null) {
            return;
        }
        KProgressHUD progress = KProgressHUD.create(requireActivity())
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .setLabel(getString(R.string.progress_title))
                .setCancellable(false)
                .show();
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.threadsCreate(user.id)
                .enqueue(new Callback<Wrappers.Single<Thread>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Single<Thread>> call,
                            @Nullable Response<Wrappers.Single<Thread>> response
                    ) {
                        int code = response != null ? response.code() : -1;
                        Log.v(TAG, "Fetching chat thread returned " + code + '.');
                        if (response != null && response.isSuccessful()) {
                            //noinspection ConstantConditions
                            Thread thread = response.body().data;
                            ((MainActivity)requireActivity())
                                    .showMessenger(thread.id, user.username);
                        }
                        progress.dismiss();
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Single<Thread>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Failed when trying to start chat.", t);
                        progress.dismiss();
                    }
                });
    }

    public static class ProfileFragmentViewModel extends ViewModel {

        public final MutableLiveData<LoadingState> state = new MutableLiveData<>(LoadingState.IDLE);
        public final MutableLiveData<User> user = new MutableLiveData<>();
    }

    private static class ProfilePagerAdapter extends FragmentStateAdapter {

        private final User mUser;

        public ProfilePagerAdapter(User user, @NonNull Fragment fragment) {
            super(fragment);
            mUser = user;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            Bundle params = new Bundle();
            switch (position) {
                case 2:
                    params.putBoolean(ClipDataSource.PARAM_SAVED, true);
                    break;
                case 1:
                    params.putBoolean(ClipDataSource.PARAM_LIKED, true);
                    break;
                default:
                    if (mUser.me) {
                        params.putBoolean(ClipDataSource.PARAM_MINE, true);
                    } else {
                        params.putInt(ClipDataSource.PARAM_USER, mUser.id);
                    }
                    break;
            }
            return ClipGridFragment.newInstance(params);
        }

        @Override
        public int getItemCount() {
            return mUser.me ? 3 : 1;
        }
    }
}
