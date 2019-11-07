package com.example.receipt2;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.soundcloud.android.crop.Crop;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.features2d.MSER;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {


    private Button capture;
    private ImageView imageView;
    private TextView textView;

    private Uri photoURI1;
    private Uri oldPhotoURI;
    private Uri tempUri;
    private String imageFilePath = "";

    boolean flagPermissions = false;
    int PERMISSION_ALL = 1;
    private static final int REQUEST_IMAGE1_CAPTURE = 1;
    private static final int PIC_CROP = 2;

    private Context context;
    private File photoFile;

    private Bitmap bitmap;

    private ProgressDialog p;

    private TesseractOCR mTess;
    private String srcText;
    Mat imageMat;

    StringBuilder sb;


    String[] PERMISSIONS = {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OpenCVLoader.initDebug();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        capture = findViewById(R.id.button);
        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);
        context = MainActivity.this;

        imageMat = new Mat();

        String language = "eng";
        mTess = new TesseractOCR(this, language);

        if (!flagPermissions) {
            checkPermissions();
        }

        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCameraIntent();
            }
        });
    }

    private void openCameraIntent(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(context.getPackageManager()) != null) {
            photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                return;
            }
            if (photoFile != null) {
                oldPhotoURI = photoURI1;
                photoURI1 = Uri.fromFile(photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI1);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE1_CAPTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_IMAGE1_CAPTURE: {
                if (resultCode == RESULT_OK) {
                    try{
                        Uri source = photoURI1;
                        Uri dest = Uri.fromFile(new File(getCacheDir(), "cropped"));
                        Crop.of(source, dest).start(MainActivity.this);
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                }
                else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, "You cancelled the operation", Toast.LENGTH_SHORT).show();
                }
            }
            case Crop.REQUEST_CROP:{
                if(resultCode == RESULT_OK){
                    tempUri = Crop.getOutput(data);
                    Log.i("hello", "Hello");
                    //imageView.setImageURI(tempUri);
                    Log.i("hello", "Hello");

                    AsyncTaskExample asyncTaskExample = new AsyncTaskExample();
                    asyncTaskExample.execute();
                }
            }
        }
    }

    public File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        imageFilePath = image.getAbsolutePath();

        return image;
    }

    void checkPermissions() {
        if (!hasPermissions(context, PERMISSIONS)) {
            requestPermissions(PERMISSIONS,
                    PERMISSION_ALL);
            flagPermissions = false;
        }
        flagPermissions = true;
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    private class AsyncTaskExample extends AsyncTask<String, String, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... strings) {
            Bitmap finalResult;

            try {
                InputStream is = context.getContentResolver().openInputStream(tempUri);
                final BitmapFactory.Options options = new BitmapFactory.Options();
                bitmap = BitmapFactory.decodeStream(is, null, options);

            } catch (Exception ex) {
                Log.i(getClass().getSimpleName(), ex.getMessage());
                Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show();
            }

            Preprocessing preprocessing = new Preprocessing();
            finalResult = preprocessing.otsu(bitmap);
            srcText = mTess.getOCRResult(finalResult);

            /*Utils.bitmapToMat(bitmap,imageMat);
            detectText(imageMat);
            Bitmap newBitmap = bitmap.copy(bitmap.getConfig(), true);
            Utils.matToBitmap(imageMat, newBitmap);*/
            return finalResult;
        }

        @Override
        protected void onPreExecute(){
            super.onPreExecute();
            p = new ProgressDialog(MainActivity.this);
            p.setMessage("Preprocessing");
            p.setIndeterminate(false);
            p.setCancelable(false);
            p.show();
        }

        @Override
        protected void onPostExecute(Bitmap semiResult){
            super.onPostExecute(bitmap);
            if(imageView != null){
                p.hide();
                imageView.setImageBitmap(semiResult);
                //imageView.setImageURI(photoURI1);

            }
            else{
                p.show();
            }
            if (srcText != null && !srcText.equals("")) {
                textView.setText(srcText);
                //textView.setText(sb.toString());
            }
        }
    }

    private void detectText(Mat mat){
        Mat imageMat2 = new Mat();

        Imgproc.cvtColor(imageMat, imageMat2, Imgproc.COLOR_RGB2GRAY);
        Mat mRgba = mat;
        Mat mGray = imageMat2;

        Scalar CONTOUR_COLOR = new Scalar(255, 0, 0, 0);
        MatOfKeyPoint keyPoint = new MatOfKeyPoint();
        List<KeyPoint> listPoint = new ArrayList<>();
        KeyPoint kPoint = new KeyPoint();
        Mat mask = Mat.zeros(mGray.size(), CvType.CV_8UC1);
        int rectanx1;
        int rectany1;
        int rectanx2;
        int rectany2;

        Scalar zeros = new Scalar(0,0,0);
        List<MatOfPoint> contour2 = new ArrayList<>();
        Mat kernel = new Mat(1, 50, CvType.CV_8UC1, Scalar.all(255));
        Mat morByte = new Mat();
        Mat hierarchy = new Mat();

        Rect rectan3 = new Rect();
        int imgSize = mRgba.height() * mRgba.width();

        if(true){
            MSER detector = MSER.create();
            detector.detect(mGray, keyPoint);
            listPoint = keyPoint.toList();
            for(int ind = 0; ind < listPoint.size(); ++ind){
                kPoint = listPoint.get(ind);
                rectanx1 = (int ) (kPoint.pt.x - 0.5 * kPoint.size);
                rectany1 = (int ) (kPoint.pt.y - 0.5 * kPoint.size);

                rectanx2 = (int) (kPoint.size);
                rectany2 = (int) (kPoint.size);
                if(rectanx1 <= 0){
                    rectanx1 = 1;
                }
                if(rectany1 <= 0){
                    rectany1 = 1;
                }
                if((rectanx1 + rectanx2) > mGray.width()){
                    rectanx2 = mGray.width() - rectanx1;
                }
                if((rectany1 + rectany2) > mGray.height()){
                    rectany2 = mGray.height() - rectany1;
                }
                Rect rectant = new Rect(rectanx1, rectany1, rectanx2, rectany2);
                Mat roi = new Mat(mask, rectant);
                roi.setTo(CONTOUR_COLOR);
            }
            Imgproc.morphologyEx(mask, morByte, Imgproc.MORPH_DILATE, kernel);
            Imgproc.findContours(morByte, contour2, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
            /*Bitmap bmp = null;
            sb = new StringBuilder();
            for(int i = 0; i<contour2.size(); ++i){
                rectan3 = Imgproc.boundingRect(contour2.get(i));
                try{
                    Mat croppedPart = mGray.submat(rectan3);
                    bmp = Bitmap.createBitmap(croppedPart.width(), croppedPart.height(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(croppedPart,bmp);
                } catch (Exception e){
                    Log.d(MainActivity.class.getSimpleName(),"Cropped part error");
                }
                if(bmp != null){
                    String str = mTess.getOCRResult(bmp);
                    if(str != null){
                        sb.append(str).append("\n");
                    }
                }
            }*/
            for(int i = 0; i<contour2.size(); ++i){
                rectan3 = Imgproc.boundingRect(contour2.get(i));
                if(rectan3.area() > 0.5 * imgSize || rectan3.area()<100 || rectan3.width / rectan3.height < 2){
                    Mat roi = new Mat(morByte, rectan3);
                    roi.setTo(zeros);
                }else{
                    Imgproc.rectangle(mRgba, rectan3.br(), rectan3.tl(), CONTOUR_COLOR);
                }
            }
        }
    }

    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

}
