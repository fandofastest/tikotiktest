package ltd.starthub.muly.fragments;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import ltd.starthub.muly.R;
import ltd.starthub.muly.activities.MainActivity;
import ltd.starthub.muly.activities.RecorderActivity;

public class MainFragment extends Fragment {

    private MainActivity.MainActivityViewModel mModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = new ViewModelProvider(requireActivity())
                .get(MainActivity.MainActivityViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewPager2 pager = view.findViewById(R.id.pager);
        pager.setAdapter(new MainAdapter(this));
        pager.setUserInputEnabled(false);
        ImageButton clips = view.findViewById(R.id.clips);
        clips.setOnClickListener(v -> pager.setCurrentItem(0, false));
        ImageButton news = view.findViewById(R.id.news);
        news.setOnClickListener(v -> pager.setCurrentItem(1, false));
        ImageButton discover = view.findViewById(R.id.discover);
        discover.setOnClickListener(v -> pager.setCurrentItem(2, false));
        View more = view.findViewById(R.id.more);
        more.setOnClickListener(v -> {
            if (mModel.isLoggedIn) {
                PopupMenu popup = new PopupMenu(requireContext(), v);
                popup.getMenuInflater().inflate(R.menu.more_menu, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                    switch (item.getItemId()) {
                        case R.id.messages:
                            pager.setCurrentItem(3, false);
                            break;
                        case R.id.notifications:
                            pager.setCurrentItem(4, false);
                            break;
                        case R.id.profile:
                            pager.setCurrentItem(5, false);
                            break;
                        default:
                            break;
                    }

                    return true;
                });
                popup.show();
            } else {
                showLoginSheet();
            }
        });
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {

            @Override
            public void onPageScrolled(int position, float offset, int offsetPx) {
                int active = ContextCompat.getColor(requireContext(), R.color.colorNavigationActive);
                int inactive = ContextCompat.getColor(requireContext(), R.color.colorNavigationInactive);
                ImageViewCompat.setImageTintList(
                        clips, ColorStateList.valueOf(position == 0 ? active : inactive));
                ImageViewCompat.setImageTintList(
                        news, ColorStateList.valueOf(position == 1 ? active : inactive));
                ImageViewCompat.setImageTintList(
                        discover, ColorStateList.valueOf(position == 2 ? active : inactive));
            }
        });
        view.findViewById(R.id.record)
            .setOnClickListener(v -> {
                if (mModel.isLoggedIn) {
                    startActivity(new Intent(requireContext(), RecorderActivity.class));
                } else {
                    showLoginSheet();
                }
            });
    }

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    private void showLoginSheet() {
        ((MainActivity)requireActivity()).showLoginSheet();
    }

    private static class MainAdapter extends FragmentStateAdapter {

        public MainAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return PlayerSliderFragment.newInstance(null);
                case 1:
                    return NewsFragment.newInstance();
                case 2:
                    return DiscoverFragment.newInstance();
                case 3:
                    return ThreadFragment.newInstance();
                case 4:
                    return NotificationFragment.newInstance();
                case 5:
                    return ProfileFragment.newInstance(null);
                default:
                    return new Fragment();
            }
        }

        @Override
        public int getItemCount() {
            return 6;
        }
    }
}
