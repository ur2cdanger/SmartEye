package edu.smarteye.sensing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MotionDetectionActivity extends SensorsActivity {

    private static final String TAG = "MotionDetectionActivity";
    private int flag = 0;
    static File f,f1;
	FileWriter filewriter;
	static BufferedWriter out,out1;
	static int STATUS = 0;
	static int STATUS1 = 1;
	String TAG1 = "MotionDetection";
	private static long lastStatusUpdate = 0;
	private int lStatus = -1;

    private static SurfaceView preview = null;
    private static SurfaceHolder previewHolder = null;
    private static Camera camera = null;
    private static boolean inPreview = false;
    private static long mReferenceTime = 0;
    private static IMotionDetection detector = null;
    private static long time = 0;
    Preferences p;

    private static volatile AtomicBoolean processing = new AtomicBoolean(false);

    /**
     * {@inheritDoc}
     */
    public void onCreate(Bundle savedInstanceState) 
    {
    	Log.v("Came ","here");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.v("Starting","Camera");
        p = new Preferences();
        File root = Environment.getExternalStorageDirectory();
		f= new File(root.getAbsolutePath(), "status.txt");  
        FileWriter filewriter = null;
		try 
		{
			filewriter = new FileWriter(f);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        out = new BufferedWriter(filewriter);
        f1= new File(root.getAbsolutePath(), "camerastatus.txt");  
        FileWriter filewriter1 = null;
		try {
			filewriter1 = new FileWriter(f1);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		out1 = new BufferedWriter(filewriter1);
        
        
        flag = 1;
        
        
        preview = (SurfaceView) findViewById(R.id.preview);
        if(preview==null)
        	Log.v("Preview","is null");
        
        previewHolder = preview.getHolder();
        if(previewHolder==null)
        	Log.v("Preview Holder","is null");
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        if (p.USE_RGB) {
            detector = new RgbMotionDetection();
        }
        else if (p.USE_LUMA) {
            detector = new LumaMotionDetection();
        } 
        else {
            // Using State based (aggregate map)
            detector = new AggregateLumaMotionDetection();
        }
        
    }

    /**
     * {@inheritDoc}
     */
    
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * {@inheritDoc}
     */
    
    public void onPause() {
        super.onPause();

        camera.setPreviewCallback(null);
        if (inPreview) camera.stopPreview();
        inPreview = false;
        camera.release();
        camera = null;
    }

    /**
     * {@inheritDoc}
     */
    
    public void onResume() {
        super.onResume();
        if(camera==null)
        	camera = Camera.open();
    }

    private PreviewCallback previewCallback = new PreviewCallback() {
       
        public void onPreviewFrame(byte[] data, Camera cam) 
        {
            if (data == null) return;
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) return;

            if (!GlobalData.isPhoneInMotion()) {
                DetectionThread thread = new DetectionThread(data, size.width, size.height);
                thread.start();
            }
        }
    };

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() 
    {       
        
        public void surfaceCreated(SurfaceHolder holder) {
            try {Thread.sleep(7000);
            	//camera = Camera.open();
                camera.setPreviewDisplay(previewHolder);
                camera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                Log.e("PreviewDemo-surfaceCallback", "Exception in setPreviewDisplay()", t);
            }
        }

        
        
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = getBestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                Log.d(TAG, "Using width=" + size.width + " height=" + size.height);
            }
            camera.setParameters(parameters);
            camera.startPreview();
            inPreview = true;
        }

       
        
        public void surfaceDestroyed(SurfaceHolder holder) {
        }
    };

    private static Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) result = size;
                }
            }
        }

        return result;
    }

    private static final class DetectionThread extends Thread {

        private byte[] data;
        private int width;
        private int height;
        Preferences p;

        public DetectionThread(byte[] data, int width, int height) {
            this.data = data;
            this.width = width;
            this.height = height;
            p = new Preferences();
        }

                
        public void run() {
            if (!processing.compareAndSet(false, true)) return;

             Log.d(TAG, "BEGIN PROCESSING...");
            try {
                // Previous frame
                int[] pre = null;
                if (p.SAVE_PREVIOUS) pre = detector.getPrevious();

                // Current frame (with changes)
                // long bConversion = System.currentTimeMillis();
                int[] img = null;
                if (p.USE_RGB) {
                    img = ImageProcessing.decodeYUV420SPtoRGB(data, width, height);
                } else {
                    img = ImageProcessing.decodeYUV420SPtoLuma(data, width, height);
                }
                // long aConversion = System.currentTimeMillis();
                // Log.d(TAG, "Conversation="+(aConversion-bConversion));

                // Current frame (without changes)
                int[] org = null;
                if (p.SAVE_ORIGINAL && img != null) org = img.clone();

                if (img != null && detector.detect(img, width, height)) {
                	
                	STATUS = 1; //for communication purposes
                	STATUS1 = 2;//for stoppin purposes
                	lastStatusUpdate = System.currentTimeMillis();
                	
                    
                    long now = System.currentTimeMillis();
                    if (now > (mReferenceTime + Preferences.PICTURE_DELAY)) {
                        mReferenceTime = now;

                        Bitmap previous = null;
                        if (p.SAVE_PREVIOUS && pre != null) {
                            if (p.USE_RGB) previous = ImageProcessing.rgbToBitmap(pre, width, height);
                            else previous = ImageProcessing.lumaToGreyscale(pre, width, height);
                        }

                        Bitmap original = null;
                        if (p.SAVE_ORIGINAL && org != null) {
                            if (p.USE_RGB) original = ImageProcessing.rgbToBitmap(org, width, height);
                            else original = ImageProcessing.lumaToGreyscale(org, width, height);

                        }

                        Bitmap bitmap = null;
                        if (p.SAVE_CHANGES && img != null) {
                            if (p.USE_RGB) bitmap = ImageProcessing.rgbToBitmap(img, width, height);
                            else bitmap = ImageProcessing.lumaToGreyscale(img, width, height);

                        }
                        
                        //added necessary files for communication purposes....
                        try
      	        	  {
      	        		  File root = Environment.getExternalStorageDirectory();
      	        		  f= new File(root.getAbsolutePath(), "status.txt");  
      	        		  FileWriter filewriter = new FileWriter(f);  
      	        		  out = new BufferedWriter(filewriter);
      	        		  out.write("FLAG"+STATUS);
      	        		  out.close();
      	        	  }
      	        	  catch(Exception e)
      	        	  {
      	        		  Log.e(TAG,"In camera "+e.getMessage());
      	        	  }
                        
                        
                        try
        	        	  {
        	        		  File root = Environment.getExternalStorageDirectory();
        	        		  f1= new File(root.getAbsolutePath(), "camerastatus.txt");  
        	        		  FileWriter filewriter = new FileWriter(f1);  
        	        		  out1 = new BufferedWriter(filewriter);
        	        		  out1.write("FLAG"+STATUS1);
        	        		  out1.close();
        	        	  }
        	        	  catch(Exception e)
        	        	  {
        	        		  Log.e(TAG,"In camera "+e.getMessage());
        	        	  }
                        

                        Log.i(TAG, "Saving.. previous=" + previous + " original=" + original + " bitmap=" + bitmap);
                        Looper.prepare();
                        new SavePhotoTask().execute(previous, original, bitmap);
                    } else {
                        Log.i(TAG, "Not taking picture because not enough time has passed since the creation of the Surface");
                    }
                    
                   // onPause();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                processing.set(false);
            }
            // Log.d(TAG, "END PROCESSING...");

            processing.set(false);
            time = System.currentTimeMillis();
            
            if((time - lastStatusUpdate) > 60000 || lastStatusUpdate == 0){
            	try
				 {
            		 STATUS = 0;
					 File root = Environment.getExternalStorageDirectory();
					 File f= new File(root.getAbsolutePath(), "status.txt");  
					 FileWriter filewriter = new FileWriter(f);  
					 BufferedWriter out = new BufferedWriter(filewriter);
					 out.write("FLAG"+STATUS);
					 out.close();
					 Log.i(TAG,"No detection in a minute "+STATUS);
				 }catch (Exception e)
				 {
					 Log.e(TAG, "In camera detection "+e.getMessage());
				 }
				 lastStatusUpdate = time;
				// lastStatus = STATUS;
            } 
       
            
        }
    };

    private static final class SavePhotoTask extends AsyncTask<Bitmap, Integer, Integer> {

                
        protected Integer doInBackground(Bitmap... data) {
            for (int i = 0; i < data.length; i++) {
                Bitmap bitmap = data[i];
                String name = String.valueOf(System.currentTimeMillis());
                if (bitmap != null) save(name, bitmap);
            }
            return 1;
        }

        private void save(String name, Bitmap bitmap) {
            File photo = new File(Environment.getExternalStorageDirectory(), name + ".jpg");
            if (photo.exists()) photo.delete();

            try {
                FileOutputStream fos = new FileOutputStream(photo.getPath());
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();
            } catch (java.io.IOException e) {
                Log.e("PictureDemo", "Exception in photoCallback", e);
            }
        }
    }
    
   
    
    
}

