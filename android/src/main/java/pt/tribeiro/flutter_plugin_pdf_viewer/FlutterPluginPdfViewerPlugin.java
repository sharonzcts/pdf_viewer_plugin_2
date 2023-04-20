package pt.tribeiro.flutter_plugin_pdf_viewer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Handler;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.FlutterEngine;
import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
/**
 * FlutterPluginPdfViewerPlugin
 */
public class FlutterPluginPdfViewerPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    private MethodChannel channel;
    private Activity activity;
    private Context context;
    private static Registrar instance;
    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private final Object pluginLocker = new Object();

    @Override
    public void onAttachedToEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        channel = new MethodChannel(binding.getBinaryMessenger(), "flutter_plugin_pdf_viewer");
        channel.setMethodCallHandler(this);
        this.context = binding.getApplicationContext();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }
    @Override
    public void onDetachedFromActivity() {}
    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }
    @Override
    public void onAttachedToActivity(ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
    }
    @Override
    public void onDetachedFromActivityForConfigChanges() {}
    public static void registerWith(Registrar registrar) {
        instance = registrar;
        //activity = registrar.activity();
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_plugin_pdf_viewer");
        channel.setMethodCallHandler(new FlutterPluginPdfViewerPlugin());
    }
    @Override
    public void onMethodCall(final MethodCall call, final Result result) {
        synchronized(pluginLocker){
            if (backgroundHandler == null) {
                handlerThread = new HandlerThread("flutterPdfViewer", Process.THREAD_PRIORITY_BACKGROUND);
                handlerThread.start();
                backgroundHandler = new Handler(handlerThread.getLooper());
            }
        }
        final Handler mainThreadHandler = new Handler();
        backgroundHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        switch (call.method) {
                            case "getNumberOfPages":
                                final String numResult = getNumberOfPages((String) call.argument("filePath"));
                                mainThreadHandler.post(new Runnable(){
                                    @Override
                                    public void run() {
                                        result.success(numResult);
                                    }
                                });
                                break;
                            case "getPage":
                                final String pageResult = getPage((String) call.argument("filePath"), (int) call.argument("pageNumber"));
                                mainThreadHandler.post(new Runnable(){
                                    @Override
                                    public void run() {
                                        result.success(pageResult);
                                    }
                                });
                                break;
                            default:
                                result.notImplemented();
                                break;
                        }
                    }
                }
        );
    }

    private String getNumberOfPages(String filePath) {
        File pdf = new File(filePath);
        try {
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY));
            Bitmap bitmap;
            int pageCount = renderer.getPageCount();
            renderer.close();
            return String.format("%d", pageCount);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private String createTempPreview(Bitmap bmp, String name, int page) {
        String filePath = name.substring(name.lastIndexOf('/') + 1);
        filePath = name.substring(name.lastIndexOf('.'));
        File file;
        try {
            String fileName = String.format("%s-%d.png", filePath, page);
            file = File.createTempFile(fileName, null, this.context.getCacheDir());
            FileOutputStream out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return file.getAbsolutePath();
    }


    private String getPage(String filePath, int pageNumber) {
        File pdf = new File(filePath);
        try {
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY));
            int pageCount = renderer.getPageCount();
            if (pageNumber > pageCount) {
                pageNumber = pageCount;
            }

            PdfRenderer.Page page = renderer.openPage(--pageNumber);

            double width = this.activity.getResources().getDisplayMetrics().densityDpi * page.getWidth();
            double height = this.activity.getResources().getDisplayMetrics().densityDpi * page.getHeight();
            //double height = 0;
            final double docRatio = width / height;

            width = 2048;
            height = (int) (width / docRatio);
            Bitmap bitmap = Bitmap.createBitmap((int) width, (int) height, Bitmap.Config.ARGB_8888);
            // Change background to white
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            // Render to bitmap
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            try {
                return createTempPreview(bitmap, filePath, pageNumber);
            } finally {
                // close the page
                page.close();
                // close the renderer
                renderer.close();
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }

        return null;
    }

}
