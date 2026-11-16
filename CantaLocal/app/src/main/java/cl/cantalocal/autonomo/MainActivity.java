package cl.cantalocal.autonomo;

import android.app.Activity;
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

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        modelDir = new File(getFilesDir(), "modelos");
        prefs = getSharedPreferences("descargas-verificadas", Context.MODE_PRIVATE);
        createSetupPage();
        File binary = new File(getApplicationInfo().nativeLibraryDir, "libace-server.so");
        if (!binary.isFile()) {
            showStatus("Falta el motor ARM64", "Esta compilación no tiene el motor ACE-Step. No se descargarán modelos innecesariamente.", 0);
            setupButton.setEnabled(false);
            return;
        }
        showStatus("CantaLocal está preparado", "Pulsa un único botón para descargar e instalar los modelos. Una vez preparados, podrás crear canciones sin internet.", 0);
        executor.execute(() -> {
            if (allTrusted()) {
                showStatus("Modelos encontrados", "Arrancando la IA musical guardada...", 95);
                try { startEngine(); } catch (Exception e) { showError(e); }
            }
        });
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
        TextView info = label("Una sola preparación con internet · Aproximadamente 3,3 GB de modelos · Después funciona sin conexión.\n\nSin Termux, sin cuentas ni copiar comandos.\n\nPrimeras pruebas recomendadas: canciones de 30 segundos, voz masculina o femenina en español.", 14, 0xffaba4be, false);
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
    private void setBusy(boolean active) {runOnUiThread(() -> setupButton.setEnabled(!active));}
    private void showError(Exception e) {
        showStatus("No se pudo completar", String.valueOf(e.getMessage()) + "\nPuedes pulsar el botón para reintentar; las descargas anteriores se conservan.", 0);
        setBusy(false);
        synchronized(lock){working=false;}
    }
    private void prepare() {
        synchronized(lock){if(working)return;working=true;}
        setBusy(true);
        executor.execute(() -> {
            try {
                if(!modelDir.isDirectory() && !modelDir.mkdirs()) throw new Exception("No se pudo crear la carpeta de modelos.");
                long required=550_000_000L;
                for(int i=0;i<MODELS.length;i++){
                    if(!isTrusted(i))required+=(long)(Long.parseLong(MODELS[i][2])*1.19);
                }
                long free=new StatFs(modelDir.getAbsolutePath()).getAvailableBytes();
                if(free<required)throw new Exception("No hay suficiente almacenamiento libre. Necesitas unos "+(required/1_000_000_000L+1)+" GB adicionales.");
                for(int i=0;i<MODELS.length;i++){
                    if(isTrusted(i))continue;
                    fetchModel(i);
                }
                startEngine();
            } catch(Exception e) {showError(e);}
            finally {synchronized(lock){working=false;}}
        });
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
    private void fetchModel(int i) throws Exception {
        final String filename=MODELS[i][0];
        File dest=model(i), part=new File(modelDir,filename+".part");
        int baseProgress=i*25;
        for(int attempt=0;attempt<4;attempt++){
            HttpURLConnection conn=null;
            try {
                long offset=part.exists()?part.length():0;
                URL url=new URL(BASE+filename+"?download=true");
                conn=(HttpURLConnection)url.openConnection();
                conn.setInstanceFollowRedirects(true);conn.setConnectTimeout(30000);conn.setReadTimeout(90000);
                conn.setRequestProperty("User-Agent","CantaLocal/0.4");
                if(offset>0)conn.setRequestProperty("Range","bytes="+offset+"-");
                int code=conn.getResponseCode();
                if(code==416){part.delete();continue;}
                if(code!=200 && code!=206)throw new Exception("Error HTTP "+code+" al descargar "+filename);
                boolean resume=code==206&&offset>0;
                if(resume){
                    String range=conn.getHeaderField("Content-Range");
                    if(range==null||!range.startsWith("bytes "+offset+"-"))throw new Exception("El servidor respondió un rango de descarga incorrecto.");
                } else offset=0;
                long total=conn.getContentLengthLong();if(total>0)total+=offset;
                long bytes=offset,last=0;
                try(InputStream input=new BufferedInputStream(conn.getInputStream(),128*1024);
                    BufferedOutputStream output=new BufferedOutputStream(new FileOutputStream(part,resume),128*1024)) {
                    byte[] buf=new byte[128*1024];int n;
                    while((n=input.read(buf))!=-1){
                        output.write(buf,0,n);bytes+=n;
                        if(bytes-last>5_000_000){
                            last=bytes;
                            int p=baseProgress+(total>0?(int)(24.0*bytes/total):0);
                            showStatus("Descargando modelo "+(i+1)+" de 4",
                              filename+"\n"+(bytes/1_000_000)+" MB recibidos"+(total>0?" / "+(total/1_000_000)+" MB":""),p);
                        }
                    }
                }
                if(part.length()<Long.parseLong(MODELS[i][2]))
                    throw new Exception("La descarga de "+filename+" quedó incompleta.");
                showStatus("Verificando modelo "+(i+1)+" de 4","Comprobando SHA-256: "+filename,baseProgress+24);
                if(!sha256(part).equalsIgnoreCase(MODELS[i][1])){
                    part.delete();throw new Exception("La firma de "+filename+" no coincide con el archivo esperado.");
                }
                Files.move(part.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);
                prefs.edit().putString(filename,signature(dest,i)).apply();
                return;
            } catch(Exception e) {
                if(attempt==3)throw e;
                Thread.sleep(2500L*(attempt+1));
            } finally {if(conn!=null)conn.disconnect();}
        }
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
        if(web!=null)return;
        web=new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest r) {
                String s=r.getUrl().toString();
                return !s.startsWith(HOST+"/");
            }
        });
        setContentView(web);
        web.loadUrl(HOST+"/");
    }
    @Override public void onBackPressed() {
        if(web!=null&&web.canGoBack())web.goBack();
        else if(web!=null){web.destroy();web=null;createSetupPage();showStatus("IA preparada","Puedes volver a abrir el estudio.",100);setupButton.setText("Abrir estudio");setupButton.setOnClickListener(v->openStudio());}
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(web!=null){web.destroy();web=null;}
        if(aceProcess!=null)aceProcess.destroy();
        executor.shutdownNow();
        super.onDestroy();
    }
}
