package com.ypx.imagepicker.activity.preview;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.ypx.imagepicker.ImagePicker;
import com.ypx.imagepicker.R;
import com.ypx.imagepicker.activity.PickerActivityManager;
import com.ypx.imagepicker.bean.ImageItem;
import com.ypx.imagepicker.bean.ImageSet;
import com.ypx.imagepicker.bean.selectconfig.MultiSelectConfig;
import com.ypx.imagepicker.data.ProgressSceneEnum;
import com.ypx.imagepicker.views.PickerUiConfig;
import com.ypx.imagepicker.data.MediaItemsDataSource;
import com.ypx.imagepicker.helper.launcher.PLauncher;
import com.ypx.imagepicker.presenter.IPickerPresenter;
import com.ypx.imagepicker.utils.PViewSizeUtils;
import com.ypx.imagepicker.views.wx.WXPreviewControllerView;
import com.ypx.imagepicker.views.base.PreviewControllerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static com.ypx.imagepicker.activity.multi.MultiImagePickerActivity.INTENT_KEY_CURRENT_INDEX;
import static com.ypx.imagepicker.activity.multi.MultiImagePickerActivity.INTENT_KEY_SELECT_CONFIG;
import static com.ypx.imagepicker.activity.multi.MultiImagePickerActivity.INTENT_KEY_PRESENTER;

/**
 * Description: 预览页面，其中包含编辑预览和普通预览
 * <p>
 * Author: peixing.yang
 * Date: 2019/2/21
 * 使用文档 ：https://github.com/yangpeixing/YImagePicker/wiki/Documentation_3.x
 */
public class MultiImagePreviewActivity extends FragmentActivity {
    static ImageSet currentImageSet;
    public static final String INTENT_KEY_SELECT_LIST = "selectList";
    private ViewPager mViewPager;
    private ArrayList<ImageItem> mSelectList;
    private ArrayList<ImageItem> mImageList;
    private int mCurrentItemPosition = 0;
    private MultiSelectConfig selectConfig;
    private IPickerPresenter presenter;
    private PickerUiConfig uiConfig;
    private WeakReference<Activity> activityWeakReference;

    public static void intent(Activity activity, ImageSet imageSet,
                              final ArrayList<ImageItem> selectList,
                              MultiSelectConfig selectConfig,
                              IPickerPresenter presenter,
                              int position, final PreviewResult result) {
        if (activity == null || selectList == null || selectConfig == null
                || presenter == null || result == null) {
            return;
        }
        if (imageSet != null) {
            currentImageSet = imageSet.copy();
        }
        Intent intent = new Intent(activity, MultiImagePreviewActivity.class);
        intent.putExtra(INTENT_KEY_SELECT_LIST, selectList);
        intent.putExtra(INTENT_KEY_SELECT_CONFIG, selectConfig);
        intent.putExtra(INTENT_KEY_PRESENTER, presenter);
        intent.putExtra(INTENT_KEY_CURRENT_INDEX, position);
        PLauncher.init(activity).startActivityForResult(intent, new PLauncher.Callback() {
            @Override
            public void onActivityResult(int resultCode, Intent data) {
                if (data != null && data.hasExtra(ImagePicker.INTENT_KEY_PICKER_RESULT)) {
                    ArrayList mList = (ArrayList) data.getSerializableExtra(ImagePicker.INTENT_KEY_PICKER_RESULT);
                    if (mList != null) {
                        result.onResult(mList, resultCode == RESULT_CANCELED);
                    }
                }
            }
        });
    }

    private ArrayList<ImageItem> filterVideo(ArrayList<ImageItem> list) {
        if (selectConfig.isCanPreviewVideo()) {
            mImageList = new ArrayList<>(list);
            return mImageList;
        }
        mImageList = new ArrayList<>();
        int videoCount = 0;
        int nowPosition = 0;
        int i = 0;
        for (ImageItem imageItem : list) {
            if (!imageItem.isVideo() && !imageItem.isGif()) {
                mImageList.add(imageItem);
            } else {
                videoCount++;
            }
            if (i == mCurrentItemPosition) {
                nowPosition = i - videoCount;
            }
            i++;
        }
        mCurrentItemPosition = nowPosition;
        return mImageList;
    }


    /**
     * 预览回调
     */
    public interface PreviewResult {
        void onResult(ArrayList<ImageItem> imageItems, boolean isCancel);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityWeakReference = new WeakReference<Activity>(this);
        if (isIntentDataFailed()) {
            finish();
        } else {
            PickerActivityManager.addActivity(this);
            setContentView(R.layout.picker_activity_image_pre);
            setUI();
            loadMediaPreviewList();
        }
    }

    /**
     * 执行返回回调
     *
     * @param isClickComplete 是否是选中
     */
    private void notifyCallBack(boolean isClickComplete) {
        Intent intent = new Intent();
        intent.putExtra(ImagePicker.INTENT_KEY_PICKER_RESULT, mSelectList);
        setResult(isClickComplete ? ImagePicker.REQ_PICKER_RESULT_CODE : RESULT_CANCELED, intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        notifyCallBack(false);
    }

    /**
     * @return 跳转数据是否合法
     */
    private boolean isIntentDataFailed() {
        if (getIntent() == null || !getIntent().hasExtra(INTENT_KEY_SELECT_CONFIG)
                || !getIntent().hasExtra(INTENT_KEY_PRESENTER)) {
            return true;
        }
        selectConfig = (MultiSelectConfig) getIntent().getSerializableExtra(INTENT_KEY_SELECT_CONFIG);
        presenter = (IPickerPresenter) getIntent().getSerializableExtra(INTENT_KEY_PRESENTER);
        mCurrentItemPosition = getIntent().getIntExtra(INTENT_KEY_CURRENT_INDEX, 0);
        ArrayList list = (ArrayList) getIntent().getSerializableExtra(INTENT_KEY_SELECT_LIST);
        if (list == null || presenter == null) {
            return true;
        }
        mSelectList = new ArrayList<>(list);
        uiConfig = presenter.getUiConfig(activityWeakReference.get());
        return false;
    }

    /**
     * 加载媒体文件夹
     */
    private void loadMediaPreviewList() {
        if (currentImageSet == null) {
            mImageList = filterVideo(mSelectList);
            initViewPager();
        } else {
            if (currentImageSet.imageItems != null && currentImageSet.imageItems.size() > 0
                    && currentImageSet.imageItems.size() >= currentImageSet.count) {
                mImageList = filterVideo(currentImageSet.imageItems);
                initViewPager();
            } else {
                final DialogInterface dialogInterface = getPresenter().
                        showProgressDialog(this, ProgressSceneEnum.loadMediaItem);
                ImagePicker.provideMediaItemsFromSet(this, currentImageSet, selectConfig.getMimeTypes(),
                        new MediaItemsDataSource.MediaItemProvider() {
                            @Override
                            public void providerMediaItems(ArrayList<ImageItem> imageItems, ImageSet allVideoSet) {
                                if (dialogInterface != null) {
                                    dialogInterface.dismiss();
                                }
                                mImageList = filterVideo(imageItems);
                                initViewPager();
                            }
                        });
            }
        }
    }


    private PreviewControllerView controllerView;

    /**
     * 初始化标题栏
     */
    private void setUI() {
        mViewPager = findViewById(R.id.viewpager);
        mViewPager.setBackgroundColor(uiConfig.getPreviewBackgroundColor());
        FrameLayout mPreviewPanel = findViewById(R.id.mPreviewPanel);

        controllerView = uiConfig.getPickerUiProvider().getPreviewControllerView(activityWeakReference.get());
        if (controllerView == null) {
            controllerView = new WXPreviewControllerView(this);
        }
        controllerView.setStatusBar();
        controllerView.initData(selectConfig, presenter, uiConfig, mSelectList);
        if (controllerView.getCompleteView() != null) {
            controllerView.getCompleteView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (PViewSizeUtils.onDoubleClick()) {
                        return;
                    }
                    notifyCallBack(true);
                }
            });
        }
        mPreviewPanel.addView(controllerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * 初始化viewpager
     */
    private void initViewPager() {
        TouchImageAdapter mAdapter = new TouchImageAdapter(this.getSupportFragmentManager());
        mViewPager.setAdapter(mAdapter);
        mViewPager.setOffscreenPageLimit(1);
        mViewPager.setCurrentItem(mCurrentItemPosition, false);
        ImageItem item = mImageList.get(mCurrentItemPosition);
        controllerView.onPageSelected(mCurrentItemPosition, item, mImageList.size());
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                mCurrentItemPosition = position;
                ImageItem item = mImageList.get(mCurrentItemPosition);
                controllerView.onPageSelected(mCurrentItemPosition, item, mImageList.size());
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }

    /**
     * 预览列表点击
     *
     * @param imageItem 当前图片
     */
    public void onPreviewItemClick(ImageItem imageItem) {
        mViewPager.setCurrentItem(mImageList.indexOf(imageItem), false);
    }

    /**
     * 单击图片
     */
    public void onImageSingleTap() {
        controllerView.singleTap();
    }

    public IPickerPresenter getPresenter() {
        return presenter;
    }

    class TouchImageAdapter extends FragmentStatePagerAdapter {
        TouchImageAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            if (mImageList == null) {
                mImageList = new ArrayList<>();
            }
        }

        @Override
        public int getCount() {
            return mImageList.size();
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            SinglePreviewFragment fragment = new SinglePreviewFragment();
            Bundle bundle = new Bundle();
            bundle.putSerializable(SinglePreviewFragment.KEY_URL, mImageList.get(position));
            fragment.setArguments(bundle);
            return fragment;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PickerActivityManager.removeActivity(this);
        if (currentImageSet != null && currentImageSet.imageItems != null) {
            currentImageSet.imageItems.clear();
            currentImageSet = null;
        }
    }
}
