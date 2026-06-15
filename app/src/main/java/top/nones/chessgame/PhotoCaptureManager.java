package top.nones.chessgame;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import Utils.LogUtils;

public class PhotoCaptureManager {

    private static final int CAMERA_COOLDOWN_MS = 2000;
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int GALLERY_REQUEST_CODE = 101;

    private final PvMActivity activity;
    private final AtomicBoolean isLaunchingCamera = new AtomicBoolean(false);
    private final AtomicBoolean isLaunchingGallery = new AtomicBoolean(false);
    private final AtomicLong lastCameraLaunchTime = new AtomicLong(0);
    private final AtomicLong lastGalleryLaunchTime = new AtomicLong(0);

    private File cameraImageFile;

    public PhotoCaptureManager(PvMActivity activity) {
        this.activity = activity;
    }

    public void handleCameraClick() {
        if (!isLaunchingCamera.compareAndSet(false, true)) {
            LogUtils.d("PvMActivity", "相机已在启动中，跳过");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCameraLaunchTime.get() < CAMERA_COOLDOWN_MS) {
            LogUtils.d("PvMActivity", "相机冷却中，跳过 (距上次启动=" + (now - lastCameraLaunchTime.get()) + "ms)");
            isLaunchingCamera.set(false);
            return;
        }

        try {
            cameraImageFile = createImageFile();
            if (cameraImageFile == null) {
                isLaunchingCamera.set(false);
                return;
            }

            Uri photoURI = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", cameraImageFile);

            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            activity.startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE);
            lastCameraLaunchTime.set(now);
            LogUtils.d("PvMActivity", "启动相机: file=" + cameraImageFile.getAbsolutePath());
        } catch (Exception ex) {
            LogUtils.e("PvMActivity", "Error launching camera: " + ex.getMessage());
            isLaunchingCamera.set(false);
        }
    }

    public void handleGalleryClick() {
        if (!isLaunchingGallery.compareAndSet(false, true)) {
            LogUtils.d("PvMActivity", "图片选择器已在启动中，跳过");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastGalleryLaunchTime.get() < CAMERA_COOLDOWN_MS) {
            LogUtils.d("PvMActivity", "图片选择器冷却中，跳过 (距上次启动=" + (now - lastGalleryLaunchTime.get()) + "ms)");
            isLaunchingGallery.set(false);
            return;
        }

        try {
            Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            activity.startActivityForResult(pickPhoto, GALLERY_REQUEST_CODE);
            lastGalleryLaunchTime.set(now);
            LogUtils.d("PvMActivity", "启动图片选择器");
        } catch (Exception ex) {
            LogUtils.e("PvMActivity", "Error launching gallery: " + ex.getMessage());
            isLaunchingGallery.set(false);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        LogUtils.d("PvMActivity", "创建图片文件: " + image.getAbsolutePath());
        return image;
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        LogUtils.d("PvMActivity", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == CAMERA_REQUEST_CODE) {
            isLaunchingCamera.set(false);
            LogUtils.d("PvMActivity", "相机标志已重置");
            if (resultCode == android.app.Activity.RESULT_OK) {
                return processCameraResult(data);
            } else {
                cleanupCameraFile();
            }
            return true;
        } else if (requestCode == GALLERY_REQUEST_CODE) {
            isLaunchingGallery.set(false);
            LogUtils.d("PvMActivity", "图片选择器标志已重置");
            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                return processGalleryResult(data);
            }
            return true;
        }
        return false;
    }

    private boolean processCameraResult(Intent data) {
        LogUtils.d("PvMActivity", "相机返回: cameraImageFile=" + (cameraImageFile != null ? cameraImageFile.getAbsolutePath() : "null"));

        Bitmap bitmap = null;
        if (cameraImageFile != null && cameraImageFile.exists()) {
            bitmap = BitmapFactory.decodeFile(cameraImageFile.getAbsolutePath());
            LogUtils.d("PvMActivity", "从文件加载图片: " + (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "null"));
        } else if (data != null && data.getExtras() != null) {
            LogUtils.d("PvMActivity", "尝试从data extras获取图片");
            bitmap = (Bitmap) data.getExtras().get("data");
            if (bitmap != null) {
                LogUtils.d("PvMActivity", "从data获取图片: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            }
        }

        if (bitmap != null) {
            bitmap = rotateImageIfNeeded(bitmap);
            bitmap = scaleImage(bitmap, 1024);
            activity.processRecognitionResult(bitmap);
            return true;
        }

        cleanupCameraFile();
        return false;
    }

    private boolean processGalleryResult(Intent data) {
        Uri selectedImage = data.getData();
        if (selectedImage == null) {
            return false;
        }

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), selectedImage);
            bitmap = scaleImage(bitmap, 1024);
            activity.processRecognitionResult(bitmap);
            return true;
        } catch (Exception e) {
            LogUtils.e("PvMActivity", "Error loading image: " + e.getMessage());
            return false;
        }
    }

    private Bitmap rotateImageIfNeeded(Bitmap bitmap) {
        if (cameraImageFile == null) {
            return bitmap;
        }

        try {
            ExifInterface exif = new ExifInterface(cameraImageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap;
            }

            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            LogUtils.d("PvMActivity", "已旋转图片: orientation=" + orientation);
            return rotatedBitmap;
        } catch (IOException e) {
            LogUtils.e("PvMActivity", "读取EXIF失败: " + e.getMessage());
            return bitmap;
        }
    }

    private Bitmap scaleImage(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float scale = (float) maxDimension / Math.max(width, height);
        if (scale >= 1.0f) {
            return bitmap;
        }

        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        bitmap.recycle();
        LogUtils.d("PvMActivity", "图片已缩放: " + newWidth + "x" + newHeight);
        return scaledBitmap;
    }

    private void cleanupCameraFile() {
        if (cameraImageFile != null && cameraImageFile.exists()) {
            cameraImageFile.delete();
        }
        cameraImageFile = null;
    }

    public void resetFlagsOnResume() {
        if (isLaunchingCamera.get()) {
            LogUtils.d("PvMActivity", "onResume: 检测到相机标志可能卡住，重置");
            isLaunchingCamera.set(false);
        }
        if (isLaunchingGallery.get()) {
            LogUtils.d("PvMActivity", "onResume: 检测到图片选择器标志可能卡住，重置");
            isLaunchingGallery.set(false);
        }
    }

    public boolean hasCameraPermission() {
        return ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        }
        return ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
}