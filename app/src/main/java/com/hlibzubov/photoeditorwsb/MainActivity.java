package com.hlibzubov.photoeditorwsb;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;



public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();

    }




    //Pytamy o uprawnienia (Aplikacja zbudowana tak ze jeżeli użytkownik nie da nam uprawnień my zamykamy program).
    private static final int REQUEST_PERMISSIONS = 1234;
    private static final String[] PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };
    private static final int PERMISSIONS_COUNT = 2;

    @SuppressLint("NewApi")
    private boolean notPermission() {
        for (int i = 0; i < PERMISSIONS_COUNT; i++) {
            if (checkSelfPermission(PERMISSIONS[i])!= PackageManager.PERMISSION_GRANTED){
                return true;
            }
        }
        return false;
    }



    @Override
    protected void onResume(){
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notPermission()){
            requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS && grantResults.length  > 0){
            if(notPermission()){

                ((ActivityManager) this.getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData();

                recreate();
            }
        }
    }



    private static final int REQUEST_PICK_IMAGE = 12345;
    private ImageView imageView;
    //Metoda init obrabia wszystkie ciskania na buttons
    private void init() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());
        }
        imageView = findViewById(R.id.imageView);
        //Znajdujemy i obrabiamy onClick dla button (selectImageButton) i dowolamy mu znaleźć tylko obrazki.

        if (!MainActivity.this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            findViewById(R.id.takePhotoButton).setVisibility(View.GONE);
        }

        final Button selectImageButton = findViewById(R.id.selectImageButton);
        selectImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                final Intent pickIntent = new Intent(Intent.ACTION_PICK);
                pickIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                final Intent chooserIntent = Intent.createChooser(intent, "Select Image");
                startActivityForResult(chooserIntent, REQUEST_PICK_IMAGE);
            }
        });
        //Znajdujemy i obrabiamy onClick dla button (takePhotoButton) i dajemy mu uprawnienia korzystać się kamerą.

        final Button takePhotoButton = findViewById(R.id.takePhotoButton);
        takePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) == null) {
                    //Twozymy fail image dla zniencza kture zrobilismy
                     final File photoFile = createImageFile();
                     imageUri = Uri.fromFile(photoFile);
                     final SharedPreferences myPreferences = getSharedPreferences(appID, 0);
                     myPreferences.edit().putString("path", photoFile.getAbsolutePath()).apply();
                     takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                     startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                } else {
                    //Jeżeli użytkownik nie ma kamery lub ona nie działa programie to wywodujemy Toast z tekstem Your Camera app is not commpatible
                    Toast.makeText(MainActivity.this,
                            "Your Camera app is not commpatible",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });


        /*Znajdujemy i obrabiamy onClick dla button (crazyFilter)
        bierzemy zdjęcie które jest rozbito na osobny pikseli i manipulujemy nimi
        w moim przypadku każdy drugi piksel staje się coraz bardziej czerwony
        tez robimy wszystko w osobnym Thread bo inaczej program zawiesi się i nie będzie działać.
        */


        final Button blackAndWhiteButton = findViewById(R.id.crazyFilter);
        blackAndWhiteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(){
                    public void run() {
                        for (int  i = 0; i < pixelCount; i++){
                            pixels[i] /=2;
                        }
                        bitmap.setPixels(pixels,0,width,0,0,width,height);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                }.start();
            }
        });

        //Znajdujemy i obrabiamy onClick dla button (saveImg) i zapisujemy obrazek.
        final Button saveImageButton = findViewById(R.id.saveImg);
        saveImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                final DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            final File outFile = createImageFile();
                            try (FileOutputStream out = new FileOutputStream(outFile)) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                                imageUri = Uri.parse("file://" + outFile.getAbsolutePath());
                                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, imageUri));
                                Toast.makeText(MainActivity.this, "The image was saved", Toast.LENGTH_SHORT).show();

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                };
                //Pytamy uzytkownika, czy na pewno chce zapisać obrazek.
                builder.setMessage("Save current photo to gallery?")
                        .setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
            }
        });

        //Znajdujemy i obrabiamy onClick dla button (back)
        //po tym, jak naciśniemy na przycisk robimy LiniearLieout editScreen (Tam, gdzie znajduje się wszystkie manipulacji z obrazkiem) niewidocznym
        //a LiniearLieout welcomeScreen widoczny
        final Button backButton = findViewById(R.id.back);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.editScreen).setVisibility(View.GONE);
                findViewById(R.id.welcomeScreen).setVisibility(View.VISIBLE);

            }
        });


    }

    private static final int REQUEST_IMAGE_CAPTURE = 1012;
    private static final String appID = "photoEditor";
    private Uri imageUri;
    //Tworzyme file IMG
    private File createImageFile(){
        final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        final String imageFileName = "/JPEG_" + timeStamp + ".jpg";
        final File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(storageDir+imageFileName);
    }

    private boolean editMode = false;
    private Bitmap bitmap;
    private int width = 0;
    private int height = 0;
    private static final int MAX_PIXEL_COUNT= 2048;
    private int[] pixels;
    private int pixelCount = 0;
        @Override
        public void onActivityResult(int requstCode, int resultCode, Intent data){
            super.onActivityResult(requstCode, resultCode,data);
            if (resultCode != RESULT_OK){
                return;
            }
            //Tu sprawdzamy ile piksel ma nasz obrazek i czy trzeba jego zmniejszyc
            if (requstCode == REQUEST_IMAGE_CAPTURE){
                if (imageUri == null){
                    final SharedPreferences p = getSharedPreferences(appID, 0);
                    final String path = p.getString("path" , "");
                    if (path.length() < 1){
                        recreate();
                        return;
                    }
                    imageUri = Uri.parse("file://" + path);
                }
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, imageUri));
            }else if (data == null){
                recreate();
                return;
            }else if (requstCode == REQUEST_PICK_IMAGE){
                imageUri = data.getData();
            }
            final ProgressDialog dialogProces = ProgressDialog.show(
                    MainActivity.this,
                    "Loading",
                    "Please Wait",
                    true);

            editMode = true;
            //robi Layout Visible i innego inVisible
            findViewById(R.id.welcomeScreen).setVisibility(View.GONE);
            findViewById(R.id.editScreen).setVisibility(View.VISIBLE);

            //tu rozbijamy obrazy na pixely
            new Thread(){
                public void run(){
                    bitmap = null;
                    final BitmapFactory.Options bmpOption = new BitmapFactory.Options();
                    bmpOption.inBitmap = bitmap;
                    bmpOption.inJustDecodeBounds = true;
                    try (InputStream input = getContentResolver().openInputStream(imageUri)){
                        bitmap = BitmapFactory.decodeStream(input, null, bmpOption);
                    }catch (IOException e) {
                        e.printStackTrace();
                    }
                    bmpOption.inJustDecodeBounds = false;
                    width = bmpOption.outWidth;
                    height = bmpOption.outHeight;
                    int resizeScale = 1;
                    if (width > MAX_PIXEL_COUNT){
                        resizeScale = width / MAX_PIXEL_COUNT;

                    }else if (height > MAX_PIXEL_COUNT){
                        resizeScale = height / MAX_PIXEL_COUNT;
                    }
                    if(width / resizeScale > MAX_PIXEL_COUNT || height / resizeScale > MAX_PIXEL_COUNT){
                        resizeScale++;
                    }

                    bmpOption.inSampleSize = resizeScale;
                    InputStream input = null;
                    try {
                        input = getContentResolver().openInputStream(imageUri);
                    }catch (FileNotFoundException e){
                        e.printStackTrace();
                        recreate();
                        return;
                    }
                    bitmap = BitmapFactory.decodeStream(input, null, bmpOption);
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            imageView.setImageBitmap(bitmap);
                            dialogProces.cancel();
                        }
                    });
                    width = bitmap.getWidth();
                    height = bitmap.getHeight();
                    bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

                    pixelCount = width * height;
                    pixels = new int[pixelCount];
                    bitmap.getPixels(pixels, 0,width,0,0, width, height);


                }
            }.start();
        }



}
