package com.example.cameraproject;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import android.os.Handler;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private TessBaseAPI BaseTess; //테서렉트 API
    private ProgressCircleDialog ProgressCircle = null; //작업중 표시
    private String DataPath = "";//언어데이터 경로
    private String CurrentPath;//촬영한 이미지 경로
    private final String[] LangList = {"eng","kor"};//사용할 traineddata 언어 종류
    private Context context; //현재 작업
    private Button btn_cap; // 촬영 버튼
    private Button btn_album;// 앨범 버튼
    private Button btn_index;// 용어사전 버튼
    private TextView view_tesstext;// 인식 결과를 출력하는 뷰
    private ImageView view_image;// 인식에 사용한 이미지를 출력하는 뷰
    private boolean ProgressFlag = false;// 작업중 표시 플래그
    private MessageHandler mhandler;// 진행 및 완료 표시

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        //뷰와 버튼을 id와 연결
        view_image = findViewById(R.id.imgview);
        view_tesstext = findViewById(R.id.txtview);
        btn_cap = findViewById(R.id.capbtn);
        btn_album = findViewById(R.id.albumbtn);
        btn_index = findViewById(R.id.indexbtn);
        //버튼에 온클릭 리스너 부여
        btn_cap.setOnClickListener(this);
        btn_album.setOnClickListener(this);
        btn_index.setOnClickListener(this);

        ProgressCircle = new ProgressCircleDialog(this);
        mhandler = new MessageHandler();
        PermissionCheck();//권한 확인
        Tesseract();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ConstantDefine.PERMISSION_CODE:
                Toast.makeText(this, "권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
                break;
            /**case ConstantDefine.REQUEST_IMAGE_PICK:
                break;
            case ConstantDefine.REQUEST_IMAGE_CROP:
                break;**/
            case ConstantDefine.ACT_TAKE_PIC:
                if ((resultCode == RESULT_OK) ) {
                    try {
                        //촬영한 파일의 bitmap이미지를 생성한다.
                        File file = new File(CurrentPath);
                        Bitmap rotatedBitmap = null;
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),
                                FileProvider.getUriForFile(MainActivity.this,
                                        getApplicationContext().getPackageName() + ".fileprovider", file));
                        if (bitmap != null) { //이미지의 회전 정보를 받아 방향을 수정한다.
                            ExifInterface ei = new ExifInterface(CurrentPath);
                            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_UNDEFINED);
                            switch (orientation) {
                                case ExifInterface.ORIENTATION_ROTATE_90:
                                    rotatedBitmap = rotateImage(bitmap, 90);
                                    break;
                                case ExifInterface.ORIENTATION_ROTATE_180:
                                    rotatedBitmap = rotateImage(bitmap, 180);
                                    break;
                                case ExifInterface.ORIENTATION_ROTATE_270:
                                    rotatedBitmap = rotateImage(bitmap, 270);
                                    break;
                                case ExifInterface.ORIENTATION_NORMAL:
                                default:
                                    rotatedBitmap = bitmap;
                            }
                            OCRThread ocrThread = new OCRThread(rotatedBitmap); //
                            ocrThread.setDaemon(true); //
                            ocrThread.start();
                            view_image.setImageBitmap(rotatedBitmap); //bitmap 이미지를 뷰에 표시
                            view_tesstext.setText(getResources().getString(R.string.LoadingMessage));
                        }
                    } catch (Exception e) {
                    }
                }
                break;
        }
    }

    @Override
    public void onClick(View v) { //세 버튼에 사용되는 onclick 이벤트
        switch (v.getId()) {
            case R.id.capbtn:
                dispatchTakePictureIntent();
                break;
            case R.id.albumbtn:
                Toast.makeText(this, "완성되지 않은 서비스입니다.", Toast.LENGTH_SHORT).show();
                break;
            case R.id.indexbtn:
                Toast.makeText(this, "완성되지 않은 서비스입니다.", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResult) {
        if (requestCode == 0) {
        } else {
        }
    }

    public static Bitmap rotateImage(Bitmap source, float angle) { //bitmap 이미지를 angle만큼 회전시킨다
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    public void PermissionCheck() { //버전이 6.0 이상이라면 권한 여부를 확인한다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED &&
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE},
                        ConstantDefine.PERMISSION_CODE);
            } else {}
        }
    }

    public void Tesseract() {
        DataPath = getFilesDir() + "/tesseract/";
        String lang = "";
        for (String Language : LangList) { //리스트의 모든 파일을 복사
            checkFile(new File(DataPath + "tessdata/"), Language);
            lang += Language + "+";
        }
        BaseTess = new TessBaseAPI();
        BaseTess.init(DataPath, lang);
    }

    private File createImageFile() throws IOException {
        // 타임스탬프를 이용하여 이미지 파일명에 중복이 없게 한다.
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        //이미지 경로를 전역변수에 저장
        CurrentPath = image.getAbsolutePath();
        //생성한 이미지 파일을 리턴
        return image;
    }

    private void dispatchTakePictureIntent() { //Intent로 카메라 앱 작동
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {//촬영한 이미지를 파일로 저장
                photoFile = createImageFile();
            } catch (IOException ex) {
            }
            if (photoFile != null) { //생성된 이미지의 URI를 다른 곳에서도 쓸 수 있도록 Output한다.
                Uri photoURI = FileProvider.getUriForFile(this,
                        this.getApplicationContext().getPackageName()+".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, ConstantDefine.ACT_TAKE_PIC);
            }
        }
    }

    private void copyFiles(String Language) {
        try {
            String filepath = DataPath + "/tessdata/" + Language + ".traineddata";
            AssetManager assetManager = getAssets();
            InputStream instream = assetManager.open("tessdata/"+Language+".traineddata");
            OutputStream outstream = new FileOutputStream(filepath);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = instream.read(buffer)) != -1) {
                outstream.write(buffer, 0, read);
            }
            outstream.flush();
            outstream.close();
            instream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkFile(File dir, String Language) {
        if (!dir.exists() && dir.mkdirs()) {
            copyFiles(Language);
        }
        if (dir.exists()) {
            String datafilepath = DataPath + "tessdata/" + Language + ".traineddata";
            File datafile = new File(datafilepath);
            if (!datafile.exists()) {
                copyFiles(Language);
            }
        }
    }

    public class OCRThread extends Thread
    {
        private Bitmap rotatedImage;
        OCRThread(Bitmap rotatedImage)
        { //스레드가 작동하는 동안 작업중임을 표시한다
            this.rotatedImage = rotatedImage;
            if(!ProgressFlag)
                ProgressCircle = ProgressCircleDialog.show(context, "", "", true);
            ProgressFlag = true;
        }
        @Override
        public void run() {
            super.run();//이미지 파일을 테서렉트로 인식하여 문자를 String으로 추출한다.
            String OCRresult = null;
            BaseTess.setImage(rotatedImage);
            //traineddata 파일이 UTF8로 인코딩되었기에 UTF8인코딩으로 글자를 받는다.
            OCRresult = BaseTess.getUTF8Text();

            Message message = Message.obtain();
            message.what = ConstantDefine.RESULT_OCR;
            message.obj = OCRresult;
            mhandler.sendMessage(message);
        }
    }
    public class MessageHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what)
            {
                case ConstantDefine.RESULT_OCR:
                    TextView OCRTextView = findViewById(R.id.txtview);
                    //OCR 결과 텍스트를 뷰에 표시
                    OCRTextView.setText(String.valueOf(msg.obj));
                    //로딩 종료
                    if(ProgressCircle.isShowing() && ProgressCircle !=null)
                        ProgressCircle.dismiss();
                    ProgressFlag = false;
                    //완료 메시지 출력
                    Toast.makeText(context,getResources().getString(R.string.CompleteMessage),Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }
}