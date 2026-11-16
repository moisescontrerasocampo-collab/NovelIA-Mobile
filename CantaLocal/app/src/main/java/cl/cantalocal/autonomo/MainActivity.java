package cl.cantalocal.autonomo;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.Uri;
import android.app.DownloadManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.StatFs;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int PORT = 8085;
    private static final String HOST = "http://127.0.0.1:8085";
    private static final String REV = "83f32e465c3abd605271d0c1c935ad2faf28c197";
    private static final String BASE = "https://huggingface.co/Serveurperso/ACE-Step-1.5-GGUF/resolve/" + REV + "/";
    private static final String[][] MODELS = {
        {"acestep-5Hz-lm-0.6B-Q8_0.gguf", "bdaf9e292d4470f31c19cafeaca1b74936a114667e3a85e5d33b65247e9908ec", "650000000"},
        {"acestep-v15-turbo-Q4_K_M.gguf", "55b4d8514850f3d0f82536f37e99673aaf48df802b5ae5b153eea32a2e2daa5e", "1300000000"},
        {"Qwen3-Embedding-0.6B-Q8_0.gguf", "972f23255e46adfe744a0eb9a0039f3c63988f65753b0968d776e8b27168c321", "700000000"},
        {"vae-BF16.gguf", "0599862ac5d15cd308e1d2e368373aea6c02e25ebd1737ad4a4562a0901b0ef8", "310000000"}
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private boolean working = false;
    private Process aceProcess;
    private File modelDir;
    private SharedPreferences prefs;
    private TextView state, description;
    private ProgressBar progress;
    private Button setupButton;
    private WebView web;
    private LinearLayout setupPage;
    private DownloadManager downloadManager;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusTick = new Runnable() { @Override public void run() { refreshDownloads(); statusHandler.postDelayed(this, 2000); } };
    private volatile boolean verifying = false;
    private volatile boolean launching = false;
    private volatile boolean studioShown = false;

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        // La pantalla puede apagarse mientras se descarga.
        File external = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        modelDir = new File(external == null ? getFilesDir() : external, "CantaLocal");
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        prefs = getSharedPreferences("descargas-verificadas", Context.MODE_PRIVATE);
        createSetupPage();
        File binary = new File(getApplicationInfo().nativeLibraryDir, "libace-server.so");
        if (!binary.isFile()) {
            showStatus("Falta el motor ARM64", "Esta compilación no tiene el motor ACE-Step. No se descargarán modelos innecesariamente.", 0);
            setupButton.setEnabled(false);
            return;
        }
        showStatus("CantaLocal está preparado", "Pulsa una vez para iniciar la descarga con notificación permanente. Puedes apagar la pantalla.", 0);
        if (allTrusted()) {
            showStatus("Modelos encontrados", "Abriendo el estudio sencillo en español…",95);
            verifyAndOpen();
        }
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String txt, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(txt); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color);
        d.setCornerRadius(dp(radius)); return d;
    }
    private void createSetupPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(15,13,27));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(26), dp(55), dp(26), dp(35));
        scroll.addView(outer);
        TextView icon = label("♫", 64, 0xffc692ff, true);
        outer.addView(icon);
        TextView name = label("CantaLocal", 37, Color.WHITE, true);
        outer.addView(name);
        TextView sub = label("Tu estudio musical con IA, dentro de tu teléfono.", 17, 0xffbab3ce, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1,-2); subParams.topMargin = dp(8);
        outer.addView(sub,subParams);

        setupPage = new LinearLayout(this);
        setupPage.setOrientation(LinearLayout.VERTICAL);
        setupPage.setPadding(dp(20),dp(23),dp(20),dp(23));
        setupPage.setBackground(rounded(0xff28243c, 19));
        LinearLayout.LayoutParams cardParam = new LinearLayout.LayoutParams(-1,-2);cardParam.topMargin=dp(34);
        outer.addView(setupPage,cardParam);
        state = label("Preparando...", 22, Color.WHITE, true);
        setupPage.addView(state);
        description = label("", 15, 0xffd9d0e9, false);
        LinearLayout.LayoutParams descParam = new LinearLayout.LayoutParams(-1,-2);descParam.topMargin=dp(10);
        setupPage.addView(description,descParam);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffbe8bf5));
        LinearLayout.LayoutParams progressParam = new LinearLayout.LayoutParams(-1,dp(11));progressParam.topMargin=dp(18);
        setupPage.addView(progress,progressParam);
        setupButton = new Button(this);
        setupButton.setText("Preparar IA musical");
        setupButton.setAllCaps(false);setupButton.setTextColor(0xff20162a);
        setupButton.setBackground(rounded(0xffd7b7ff, 13));
        LinearLayout.LayoutParams bParam = new LinearLayout.LayoutParams(-1,dp(56));bParam.topMargin=dp(24);
        setupPage.addView(setupButton,bParam);
        setupButton.setOnClickListener(v -> prepare());
        TextView info = label("Descarga con notificación fija de Android. Puedes salir de la app y apagar la pantalla. Cada fragmento se guarda para reanudar tras cortes de Wi-Fi.\n\nCuando Android lo solicite, permite las notificaciones y la actividad sin restricción de batería.\n\nAproximadamente 3,3 GB · Sin Termux.", 14, 0xffaba4be, false);
        LinearLayout.LayoutParams infoParam = new LinearLayout.LayoutParams(-1,-2); infoParam.topMargin=dp(27);
        outer.addView(info,infoParam);
        setContentView(scroll);
    }
    private void showStatus(String title,String detail,int percent) {
        runOnUiThread(() -> {
            if (state == null) return;
            state.setText(title); description.setText(detail);
            progress.setProgress(Math.min(100,Math.max(0,percent)));
        });
    }
    private void setBusy(boolean active) {
        runOnUiThread(() -> {
            if (setupButton == null) return;
            setupButton.setEnabled(!active);
            setupButton.setText(active ? "Preparando IA musical…" : "Preparar IA musical");
            setupButton.setAlpha(active ? 0.72f : 1.0f);
        });
    }
    private void showError(Exception e) {
        showStatus("No se pudo completar", (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\nPuedes pulsar el botón para reintentar; las descargas anteriores se conservan.", 0);
        setBusy(false);
        synchronized(lock){working=false;}
    }
    private static final int REQUEST_NOTIFICATIONS = 1402;
    private void prepare() {
        if (Build.VERSION.SDK_INT >= 33 &&
             checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIFICATIONS);
            return;
        }
        startBackgroundDownloads();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==REQUEST_NOTIFICATIONS)startBackgroundDownloads();
    }
    private void startBackgroundDownloads() {
        // Cancelar DownloadManager anterior para impedir escrituras simultáneas.
        if(!prefs.getBoolean("downloadmanager-migrated-v06",false)){
            for(int i=0;i<MODELS.length;i++){
                long id=prefs.getLong("download-id-"+MODELS[i][0],-1L);
                if(id>0)try{downloadManager.remove(id);}catch(Exception ignored){}
            }
            prefs.edit().putBoolean("downloadmanager-migrated-v06",true).commit();
        }
        showStatus("Descarga en segundo plano","Iniciando la notificación permanente. Puedes apagar la pantalla…",1);
        try {
            Intent job=new Intent(this,ModelDownloadService.class).setAction(ModelDownloadService.ACTION_START);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(job);else startService(job);
        }catch(Exception e){
            showStatus("No se pudo iniciar la descarga",String.valueOf(e.getMessage()),0);
            return;
        }
        // MagicOS puede impedir descargas prolongadas; Android pide autorización una sola vez.
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm!=null && Build.VERSION.SDK_INT>=23 && !pm.isIgnoringBatteryOptimizations(getPackageName())
            && !prefs.getBoolean("requested-battery-exemption",false)) {
            prefs.edit().putBoolean("requested-battery-exemption",true).apply();
            try{
                Intent request=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:"+getPackageName()));
                startActivity(request);
            }catch(Exception ignored){}
        }
    }
    private void refreshDownloads() {
        if(web!=null || prefs==null || modelDir==null)return;
        if(allTrusted()){
            if(studioShown) {
                showStatus("Estudio musical listo","Abre el estudio para escribir tus letras y generar canciones.",100);
                setupButton.setText("Abrir estudio musical");
                setupButton.setOnClickListener(v -> openStudio());
            } else {
                showStatus("Modelos verificados","Preparando el estudio en español…",100);
                if(!verifying && !launching)verifyAndOpen();
            }
            return;
        }
        String st=prefs.getString("dl-state","");
        String msg=prefs.getString("dl-message","");
        int pct=prefs.getInt("dl-progress",0);
        if(st.isEmpty()&&pct==0)return;
        if("descargando".equals(st)||"iniciando".equals(st)) {
            showStatus("Descargando en segundo plano",msg+"\nPuedes bloquear la pantalla. Revisa la notificación de CantaLocal.",pct);
            setupButton.setText("Comprobar / continuar");
        }else if("pausado".equals(st)){
            showStatus("Descarga pausada",msg+"\nPulsa para reanudar los archivos parciales.",pct);
            setupButton.setText("Reanudar descarga");
        }else if("error".equals(st)){
            showStatus("Descarga interrumpida",msg+"\nPulsa para continuar con lo que ya se guardó.",pct);
            setupButton.setText("Reintentar descarga");
        }else if("completado".equals(st)){
            showStatus("Modelos descargados","Comprobando archivos para iniciar el estudio…",100);
            if(!verifying && !launching)verifyAndOpen();
        }else{
            showStatus("Preparando IA musical",msg,pct);
        }
    }
    private void verifyAndOpen() {
        synchronized(lock){if(verifying||launching||web!=null)return;verifying=true;}
        executor.execute(()->{
            try {
                if(!allTrusted())throw new Exception("Todavía falta verificar un modelo");
                synchronized(lock){launching=true;}
                startEngine();
            }catch(Exception e){showError(e);}
            finally{synchronized(lock){verifying=false;launching=false;}}
        });
    }
    @Override protected void onResume(){
        super.onResume();
        statusHandler.removeCallbacks(statusTick);
        statusHandler.post(statusTick);
    }
    @Override protected void onPause(){
        statusHandler.removeCallbacks(statusTick);
        super.onPause();
    }

    private boolean allTrusted() { for(int i=0;i<MODELS.length;i++)if(!isTrusted(i))return false;return true; }
    private File model(int i){return new File(modelDir,MODELS[i][0]);}
    private String signature(File f, int i) {return f.length()+":"+f.lastModified()+":"+MODELS[i][1];}
    private boolean isTrusted(int i) {
        File f=model(i);
        return f.isFile() && f.length()>=Long.parseLong(MODELS[i][2]) &&
               signature(f,i).equals(prefs.getString(MODELS[i][0],""));
    }
    private String sha256(File f) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(f),256*1024)) {
            byte[] buf=new byte[256*1024]; int n;
            while((n=in.read(buf))!=-1)md.update(buf,0,n);
        }
        StringBuilder s=new StringBuilder();
        for(byte b:md.digest())s.append(String.format(Locale.ROOT,"%02x",b&0xff));
        return s.toString();
    }
    private boolean health() {
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(HOST+"/health").openConnection();
            c.setConnectTimeout(1500);c.setReadTimeout(1500);
            if(c.getResponseCode()!=200)return false;
            try(InputStream in=c.getInputStream()){
                byte[] b=new byte[100];
                int n=in.read(b);
                return n>0 && new String(b,0,n,"UTF-8").contains("ok");
            }
        }catch(Exception ex){return false;}
        finally {if(c!=null)c.disconnect();}
    }
    private void startEngine() throws Exception {
        showStatus("Iniciando motor musical","Cargando ACE-Step. La primera ejecución puede ser más lenta.",98);
        File exe=new File(getApplicationInfo().nativeLibraryDir,"libace-server.so");
        if(!exe.canExecute())throw new Exception("Android impide ejecutar el motor ARM64 de esta APK.");
        if(!health()) {
            ProcessBuilder pb=new ProcessBuilder(exe.getAbsolutePath(),
                "--models",modelDir.getAbsolutePath(),"--host","127.0.0.1","--port",Integer.toString(PORT),
                "--device","CPU","--max-batch","1","--max-seq","4096","--vae-chunk","128","--vae-overlap","32");
            pb.directory(getFilesDir());
            pb.environment().put("HOME",getFilesDir().getAbsolutePath());
            pb.environment().put("LD_LIBRARY_PATH",getApplicationInfo().nativeLibraryDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(getFilesDir(),"motor.log")));
            aceProcess=pb.start();
            for(int n=0;n<80;n++){
                if(health())break;
                if(!aceProcess.isAlive())throw new Exception("El motor se cerró al iniciar. Es posible que no sea compatible con este teléfono.");
                Thread.sleep(1500);
            }
        }
        if(!health())throw new Exception("El motor no respondió después de arrancar.");
        showStatus("¡IA musical preparada!","Abriendo el estudio para crear canciones con letras cantadas.",100);
        runOnUiThread(() -> openStudio());
    }
    private void openStudio() {
        // Experiencia simple en español: no abrir el panel técnico de acestep.cpp.
        if (isFinishing()) return;
        studioShown = true;
        startActivity(new android.content.Intent(this, StudioActivity.class));
        showStatus("Estudio musical abierto", "Al regresar puedes volver a entrar al estudio.",100);
        setupButton.setText("Abrir estudio musical");
        setupButton.setOnClickListener(v -> openStudio());
    }
    @Override public void onBackPressed() {
        if(web!=null&&web.canGoBack())web.goBack();
        else if(web!=null){web.destroy();web=null;createSetupPage();showStatus("IA preparada","Puedes volver a abrir el estudio.",100);setupButton.setText("Abrir estudio");setupButton.setOnClickListener(v->openStudio());}
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(web!=null){web.destroy();web=null;}
        if(aceProcess!=null)aceProcess.destroy();
        statusHandler.removeCallbacks(statusTick);
        executor.shutdownNow();
        super.onDestroy();
    }
}
