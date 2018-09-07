package com.an.opencamera.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.an.opencamera.util.AppUtil;
import com.an.opencamera.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    // Constants
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_RUNTIME_PERMISSION_CAMERA = 400;
    private static final int REQUEST_CAMERA_CAPTURE_IMAGE = 401;
    private static final int REQUEST_RUNTIME_PERMISSION_EXTERNAL_STORAGE = 402;

    // UI Components
    private Button mBtnOpenCamera;
    private ImageView mIvLastCapturedImage;

    // Other Objects
    private Uri mImageUri;
    private String mImagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
    }

    private void initView() {
        mBtnOpenCamera = findViewById(R.id.activity_main_btn_open_camera);
        mBtnOpenCamera.setOnClickListener(this);

        mIvLastCapturedImage = findViewById(R.id.activity_main_iv_last_captured_image);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.activity_main_btn_open_camera:
                requestRunTimePermissionForAccessCamera();
                break;
            default:
                break;
        }
    }

    // request runtime permissions for camera
    private void requestRunTimePermissionForAccessCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_RUNTIME_PERMISSION_CAMERA);
            Toast.makeText(MainActivity.this, "Need this permission for Open Camera", Toast.LENGTH_LONG).show();
        } else {
            openCameraIntent();
        }
    }

    private void openCameraIntent() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Pic_1");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From M.DMS app");
        mImageUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);

        startActivityForResult(intent, REQUEST_CAMERA_CAPTURE_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CAMERA_CAPTURE_IMAGE:
                if (resultCode == RESULT_OK) {
                    requestRunTimePermissionForAccessExternalStorage();
                } else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, "Image not Captured", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    // request runtime permissions for access external storage
    private void requestRunTimePermissionForAccessExternalStorage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_RUNTIME_PERMISSION_EXTERNAL_STORAGE);
            Toast.makeText(MainActivity.this, "Need this permission for save photos", Toast.LENGTH_LONG).show();
        } else {
            onCaptureImageResult();
        }
    }

    private void onCaptureImageResult() {
        final Dialog[] dialogProgress = new Dialog[1];
        Observable.just(mImageUri)
                // map -> doInBackground (first arg input, second arg output)
                .map(new Func1<Uri, Bitmap>() {
                    @Override
                    public Bitmap call(Uri imageUri) {
                        try {

                            Bitmap mBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), mImageUri);
                            String imageLocation = AppUtil.getRealPathFromURI(MainActivity.this, mImageUri);

                            ExifInterface ei = new ExifInterface(imageLocation);
                            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_UNDEFINED);

                            Bitmap rotatedBitmap = null;
                            switch (orientation) {

                                case ExifInterface.ORIENTATION_ROTATE_90:
                                    rotatedBitmap = AppUtil.rotateImage(mBitmap, 90);
                                    break;

                                case ExifInterface.ORIENTATION_ROTATE_180:
                                    rotatedBitmap = AppUtil.rotateImage(mBitmap, 180);
                                    break;

                                case ExifInterface.ORIENTATION_ROTATE_270:
                                    rotatedBitmap = AppUtil.rotateImage(mBitmap, 270);
                                    break;

                                case ExifInterface.ORIENTATION_NORMAL:
                                default:
                                    rotatedBitmap = mBitmap;
                            }

                            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                return null;
                            }

                            Bitmap savedFileBitmap = saveToExternal(rotatedBitmap);

                            return savedFileBitmap;

                        } catch (Exception ex) {
                            ex.printStackTrace();
                            Toast.makeText(MainActivity.this, "Could not load the image", Toast.LENGTH_SHORT).show();
                        }
                        return null;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                // doOnSubscribe -> onPreExecute
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        dialogProgress[0] = AppUtil.showProgress(MainActivity.this);
                    }
                })
                // subscribe -> onPostExecute
                .subscribe(new Action1<Bitmap>() {
                    @Override
                    public void call(Bitmap createdImageBitmap) {
                        if (createdImageBitmap != null) {
                            mIvLastCapturedImage.setImageBitmap(createdImageBitmap);

                            Toast.makeText(MainActivity.this, "Image stored\n" + mImagePath, Toast.LENGTH_LONG).show();
                            dialogProgress[0].dismiss();

                        } else {
                            dialogProgress[0].dismiss();
                            Toast.makeText(MainActivity.this, "Image not saved", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public Bitmap saveToExternal(Bitmap bitmap) {
        File filesDir = new File(Environment.getExternalStorageDirectory(), "Open Camera");

        // Create the storage directory if it does not exist
        if (!filesDir.exists()) {
            if (!filesDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory");
            }
        }
        File checkFile = new File(filesDir, System.currentTimeMillis() + ".jpg");

        if (filesDir != null && filesDir.isDirectory() && filesDir.exists()) {
            try {
                FileOutputStream out = new FileOutputStream(checkFile);

                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

                out.flush();
                out.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mImagePath = checkFile.getPath();
        Bitmap myBitmap = BitmapFactory.decodeFile(checkFile.getAbsolutePath());

        return myBitmap;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "----> onRequestPermissionResult is called <----");

        switch (requestCode) {
            case REQUEST_RUNTIME_PERMISSION_CAMERA:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "----> Camera permission granted <----");
                    openCameraIntent();
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Log.i(TAG, "----> Camera permission denied <----");
                    boolean showRational = ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0]);
                    if (showRational) {
                        Log.i(TAG, "----> Checkbox unchecked ---->");
                        //requestRunTimePermissionForAccessCamera();
                        Toast.makeText(MainActivity.this, "Need this permission for take photos", Toast.LENGTH_LONG).show();
                    } else {
                        Log.i(TAG, "----> Checkbox checked ---->");
                        Toast.makeText(MainActivity.this, "Please turn on camera permissions from settings", Toast.LENGTH_LONG).show();
                    }
                }
                break;

            case REQUEST_RUNTIME_PERMISSION_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "----> External storage permission granted <----");
                    onCaptureImageResult();
                } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Log.i(TAG, "----> External storage permission denied <----");
                    boolean showRational = ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0]);
                    if (showRational) {
                        Log.i(TAG, "----> Checkbox unchecked ---->");
                        //requestRunTimePermissionForAccessExternalStorage(mData);
                        Toast.makeText(MainActivity.this, "Need this permission for save photos", Toast.LENGTH_LONG).show();
                    } else {
                        Log.i(TAG, "----> Checkbox checked ---->");
                        Toast.makeText(MainActivity.this, "Please turn on storage permissions from settings", Toast.LENGTH_LONG).show();
                    }
                }
                break;

            default:
        }
    }

}
