package com.example.receipt2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Preprocessing {

    private static final String TAG = "MainActivity";

    public Bitmap rotate(Bitmap bitmap){
        Mat rgbMat = new Mat();
        Mat grayMat = new Mat();
        Mat cannyMat = new Mat();
        Bitmap finalImage;
        double maxValue = 0;
        int maxValueId = 0;

        Utils.bitmapToMat(bitmap, rgbMat);

        Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY);
        Imgproc.Canny(grayMat, cannyMat, 100, 200);

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(cannyMat, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        for(int i = 0; i < contours.size(); i++){
            double contourArea = Imgproc.contourArea(contours.get(i));

            if(maxValue < contourArea){
                maxValue = contourArea;
                maxValueId = i;
            }
        }

        MatOfPoint2f point2f = new MatOfPoint2f(contours.get(maxValueId).toArray());
        RotatedRect box = Imgproc.minAreaRect(point2f);
        Log.i("Rotated rect_angle", "" + box.angle);
        Log.i("Rotated rect_angle", "" + box.size);
        Point points[] = new Point[4];
        box.points(points);

        Mat result = deskew(rgbMat, box.angle, box.size.height, box.size.width);


        Mat gray = new Mat();;
        Mat canny = new Mat();
        Mat hierac = new Mat();

        List<MatOfPoint> contours_B = new ArrayList<MatOfPoint>();
        Imgproc.cvtColor(result, gray, Imgproc.COLOR_RGB2GRAY);
        Imgproc.Canny(gray, canny, 100, 200);
        Imgproc.findContours(canny, contours_B, hierac, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        finalImage = Bitmap.createBitmap(rgbMat.cols(),rgbMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(result, finalImage);
        Log.i(TAG, "procSrc2Gray sucess...");

        return finalImage;
    }

    public Bitmap crop(Bitmap bitmap){
        Mat rgbMat = new Mat();
        Mat grayMat = new Mat();
        Mat cannyMat = new Mat();
        Bitmap finalImage;
        double maxValue = 0;
        int maxValueId = 0;

        Utils.bitmapToMat(bitmap, rgbMat);

        Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY);
        Imgproc.Canny(grayMat, cannyMat, 100, 200);

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(cannyMat, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        for(int i = 0; i < contours.size(); i++){
            double contourArea = Imgproc.contourArea(contours.get(i));

            if(maxValue < contourArea){
                maxValue = contourArea;
                maxValueId = i;
            }
        }

        MatOfPoint2f point2f = new MatOfPoint2f(contours.get(maxValueId).toArray());
        RotatedRect box = Imgproc.minAreaRect(point2f);
        Log.i("Rotated rect_angle", "" + box.angle);
        Log.i("Rotated rect_angle", "" + box.size);
        Point points[] = new Point[4];
        Point x[] = new Point[1];
        box.points(points);
        x[0] = points[0];
        for(int i=0; i<4; ++i) {
            Imgproc.line(rgbMat, points[i], points[(i + 1) % 4], new Scalar(255, 255, 255));
            Log.i("x point[" + i + "]", Double.toString(points[i].x));
            Log.i("y point[" + i + "]", Double.toString(points[i].y));
            if (x[0].x > points[i].x) {
                if (x[0].y > points[i].y) {
                    x[0].x = points[i].x;
                    x[0].y = points[i].y;
                }
            }
        }

        Log.i("point", "" + x[0]);
        Log.i("width", "" + box.size.width);
        Log.i("height", "" + box.size.height);

        finalImage = Bitmap.createBitmap(rgbMat.cols(),rgbMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rgbMat, finalImage);
        Log.i(TAG, "procSrc2Gray sucess...");

        Bitmap cutBitmap = Bitmap.createBitmap((int)box.size.width, (int)box.size.height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(cutBitmap);
        Rect desRect = new Rect(0, 0, (int)box.size.width, (int)box.size.height);
        Rect srcRect = new Rect((int)x[0].x, (int)x[0].y, (int)(box.size.width *1.5) , (int)(box.size.height*1.5));
        canvas.drawBitmap(bitmap, srcRect, desRect, null);
        return cutBitmap;
    }

    private int getRandomUniformInt(int min, int max) {
        Random r1 = new Random();
        return r1.nextInt() * (max - min) + min;
    }

    public Mat deskew(Mat src, double angle, double height, double width) {
        Point center = new Point(src.width()/2, src.height()/2);

        if(width > height){
            angle = 90 + angle;
        }

        Mat rotImage = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        //1.0 means 100 % scale
        Size size = new Size(src.width(), src.height());
        Imgproc.warpAffine(src, src, rotImage, size, Imgproc.INTER_NEAREST);
        return src;
    }

    public Mat warp(Mat inputMat,Mat startM) {
        int resultWidth = 1000;
        int resultHeight = 1000;

        Mat outputMat = new Mat(resultWidth, resultHeight, CvType.CV_8UC4);



        Point ocvPOut1 = new Point(0, 0);
        Point ocvPOut2 = new Point(0, resultHeight);
        Point ocvPOut3 = new Point(resultWidth, resultHeight);
        Point ocvPOut4 = new Point(resultWidth, 0);
        List<Point> dest = new ArrayList<Point>();
        dest.add(ocvPOut1);
        dest.add(ocvPOut2);
        dest.add(ocvPOut3);
        dest.add(ocvPOut4);
        Mat endM = Converters.vector_Point2f_to_Mat(dest);

        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(startM, endM);

        Imgproc.warpPerspective(inputMat,
                outputMat,
                perspectiveTransform,
                new Size(resultWidth, resultHeight),
                Imgproc.INTER_CUBIC);

        return outputMat;
    }

    public Bitmap otsu(Bitmap bitmap){
        Mat rgbMat = new Mat();
        Mat grayMat = new Mat();
        Mat otsuMat = new Mat();
        Mat erosion = new Mat();
        Mat dilation = new Mat();
        Bitmap finalImage;

        Utils.bitmapToMat(bitmap, rgbMat);

        Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY);
        Imgproc.threshold(grayMat, otsuMat, 0, 255, Imgproc.THRESH_OTSU );

        //Imgproc.dilate(otsuMat, dilation, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2)));
        //Imgproc.erode(dilation, erosion, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2,2)));

        Imgproc.erode(otsuMat, erosion, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10,10)));
        Imgproc.dilate(erosion, dilation, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));

        /*finalImage = Bitmap.createBitmap(rgbMat.cols(),rgbMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(erosion, finalImage);
        Log.i(TAG, "procSrc2Gray sucess...");*/

        finalImage = Bitmap.createBitmap(rgbMat.cols(),rgbMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(dilation, finalImage);
        Log.i(TAG, "procSrc2Gray sucess...");

        return finalImage;
    }


}
